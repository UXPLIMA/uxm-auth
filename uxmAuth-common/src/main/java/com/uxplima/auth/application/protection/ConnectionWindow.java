package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.protection.AddressRanges;

/**
 * The door's rule on connections: an address may connect so many times inside a sliding window, counted at
 * every arrival whatever comes of it, because a bot that registers never fails a login. The count lives in the
 * memory of the door that meets the connection. A refused arrival counts too, so an address that keeps knocking
 * stays out until it stops.
 */
public final class ConnectionWindow implements Admission.Rule {

    /** How often a check also lets go of the addresses that went quiet. */
    private static final long SWEEP_EVERY = 1024;

    /**
     * How many connections an address may make inside the window, and the addresses never counted.
     *
     * @param perAddress the connections one address may make inside the window
     * @param window how far back a connection still counts
     * @param exempt the addresses and ranges never counted, such as the server's own proxies
     */
    public record Settings(int perAddress, Duration window, AddressRanges exempt) {
        public Settings {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(exempt, "exempt");
            if (perAddress < 1) {
                throw new IllegalArgumentException("An address needs at least one connection, got: " + perAddress);
            }
            if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("The window must be longer than nothing");
            }
        }
    }

    /** The latest connections of one address, oldest first, never more than the limit. */
    private record Recent(List<Instant> times) {
        Recent {
            times = List.copyOf(times);
        }
    }

    private final Map<String, Recent> recent = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();
    private final Supplier<Settings> settings;
    private final Supplier<Instant> clock;

    public ConnectionWindow(Supplier<Settings> settings, Supplier<Instant> clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<Admission.Refused> check(Admission.Arrival arrival) {
        Objects.requireNonNull(arrival, "arrival");
        Settings now = settings.get();
        if (now.exempt().contains(arrival.address())) {
            return Optional.empty();
        }
        if (checks.incrementAndGet() % SWEEP_EVERY == 0) {
            sweep();
        }
        Instant at = clock.get();
        AtomicReference<Duration> refusedFor = new AtomicReference<>();
        recent.compute(AddressKey.of(arrival.address()), (key, before) -> {
            List<Instant> live = live(before, at, now.window());
            if (live.size() >= now.perAddress()) {
                refusedFor.set(Duration.between(at, live.getFirst().plus(now.window())));
            }
            live.add(at);
            return new Recent(live.subList(Math.max(0, live.size() - now.perAddress()), live.size()));
        });
        Duration left = refusedFor.get();
        return left == null
                ? Optional.empty()
                : Optional.of(new Admission.Refused(Keys.KICK_TOO_MANY_CONNECTIONS, Map.of(), left));
    }

    /** Lets go of every address whose latest connection is a window old. */
    public void sweep() {
        Instant at = clock.get();
        Duration window = settings.get().window();
        for (String key : List.copyOf(recent.keySet())) {
            recent.computeIfPresent(key, (k, kept) -> live(kept, at, window).isEmpty() ? null : kept);
        }
    }

    /** How many connections are kept for this address, for a test to read. */
    int kept(String address) {
        Recent kept = recent.get(AddressKey.of(Objects.requireNonNull(address, "address")));
        return kept == null ? 0 : kept.times().size();
    }

    /** How many addresses are kept, for a test to read. */
    int addresses() {
        return recent.size();
    }

    private static List<Instant> live(Recent kept, Instant at, Duration window) {
        List<Instant> live = new ArrayList<>();
        if (kept != null) {
            Instant from = at.minus(window);
            kept.times().stream().filter(time -> time.isAfter(from)).forEach(live::add);
        }
        return live;
    }
}
