package com.uxplima.auth.velocity.adapter.inbound;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The connections the proxy settled before Velocity knows them as players, by the id and the name each one waits
 * under. LimboAPI holds a player in the proxy's world before Velocity registers them, so Velocity's own check for a
 * second connection under a name does not see them; this does, and a second connection under the same id or name
 * is turned away while the first one is held.
 *
 * <p>A connection is held from the moment it is settled until it leaves, is let go, or outlives the longest wait,
 * by when Velocity knows it as a player and its own check takes over. A name is compared without its case.
 */
public final class Waiters {

    /** What one connection holds, and until when. */
    private record Held(UUID id, String name, Instant until) {}

    private final Map<InetSocketAddress, Held> held = new HashMap<>();
    private final Clock clock;
    private final Supplier<Duration> keep;

    /** @param keep how long a connection is held at most: longer than a player may wait in the proxy's world */
    public Waiters(Clock clock, Supplier<Duration> keep) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.keep = Objects.requireNonNull(keep, "keep");
    }

    /**
     * The connection at this address holds this id and this name now, unless another connection holds either of
     * them; false then, and nothing is held.
     */
    public synchronized boolean hold(InetSocketAddress connection, UUID id, String name) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(id, "id");
        String lower = Objects.requireNonNull(name, "name").toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        held.values().removeIf(each -> each.until().isBefore(now));
        for (Map.Entry<InetSocketAddress, Held> each : held.entrySet()) {
            if (!each.getKey().equals(connection)
                    && (each.getValue().id().equals(id)
                            || each.getValue().name().equals(lower))) {
                return false;
            }
        }
        held.put(connection, new Held(id, lower, now.plus(keep.get())));
        return true;
    }

    /** Whether a connection other than this one holds this id. */
    public synchronized boolean heldByAnother(InetSocketAddress connection, UUID id) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(id, "id");
        Instant now = clock.instant();
        return held.entrySet().stream()
                .anyMatch(each -> !each.getKey().equals(connection)
                        && each.getValue().id().equals(id)
                        && !each.getValue().until().isBefore(now));
    }

    /** The connection left, or no longer waits: what it held is let go, and the id it held is answered. */
    public synchronized Optional<UUID> release(InetSocketAddress connection) {
        return Optional.ofNullable(held.remove(Objects.requireNonNull(connection, "connection")))
                .map(Held::id);
    }
}
