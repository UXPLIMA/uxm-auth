package com.uxplima.auth.application.premium;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.auth.domain.name.PlayerName;

/**
 * Something a connection did lately, remembered by its address and name for a time the operator sets.
 *
 * <p>Two of these exist. One holds the connections that failed the handshake: the client of a failed
 * handshake reads only "Invalid session", which nobody can change, and this memory is how the next connection
 * from there is told in plain words what happened. The other holds claims: a claim asks only the next
 * connection from the address that made it for the handshake, so nobody can hand the cracked owner of a name
 * a failed one.
 */
public final class NameMemory {

    /** Past this many remembered entries, every new one first lets the expired ones go. */
    private static final int SWEEP_PAST = 256;

    private final Supplier<Duration> keepFor;
    private final Clock clock;
    private final Map<String, Instant> until = new ConcurrentHashMap<>();

    public NameMemory(Supplier<Duration> keepFor, Clock clock) {
        this.keepFor = Objects.requireNonNull(keepFor, "keepFor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A connection from this address under this name did it just now. */
    public void remember(String address, PlayerName name) {
        Instant now = clock.instant();
        until.put(key(address, name), now.plus(keepFor.get()));
        if (until.size() > SWEEP_PAST) {
            until.values().removeIf(end -> !now.isBefore(end));
        }
    }

    /** Whether a connection from this address under this name did it lately. */
    public boolean holds(String address, PlayerName name) {
        Instant end = until.get(key(address, name));
        return end != null && clock.instant().isBefore(end);
    }

    /** What was remembered for this address and name is old news. */
    public void forget(String address, PlayerName name) {
        until.remove(key(address, name));
    }

    private static String key(String address, PlayerName name) {
        return Objects.requireNonNull(address, "address") + "/"
                + Objects.requireNonNull(name, "name").key();
    }

    /** How many entries are remembered, for a test that proves old ones are let go. */
    int remembered() {
        return until.size();
    }
}
