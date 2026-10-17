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
 * The connections that failed the handshake lately, by address and name.
 *
 * <p>The client of a failed handshake reads only "Invalid session", which nobody can change. This memory is
 * how the next connection from there is told in plain words what happened, and, under the automatic rule,
 * let in with a password instead of failing again.
 */
public final class FailedHandshakes {

    /** Past this many remembered failures, every new one first lets the expired ones go. */
    private static final int SWEEP_PAST = 256;

    private final Supplier<Duration> keepFor;
    private final Clock clock;
    private final Map<String, Instant> until = new ConcurrentHashMap<>();

    public FailedHandshakes(Supplier<Duration> keepFor, Clock clock) {
        this.keepFor = Objects.requireNonNull(keepFor, "keepFor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** A connection from this address under this name failed the handshake just now. */
    public void remember(String address, PlayerName name) {
        Instant now = clock.instant();
        until.put(key(address, name), now.plus(keepFor.get()));
        if (until.size() > SWEEP_PAST) {
            until.values().removeIf(end -> !now.isBefore(end));
        }
    }

    /** Whether a connection from this address under this name failed the handshake lately. */
    public boolean failedBefore(String address, PlayerName name) {
        Instant end = until.get(key(address, name));
        return end != null && clock.instant().isBefore(end);
    }

    /** A connection from this address under this name passed the handshake, so the failure is old news. */
    public void forget(String address, PlayerName name) {
        until.remove(key(address, name));
    }

    private static String key(String address, PlayerName name) {
        return Objects.requireNonNull(address, "address") + "/"
                + Objects.requireNonNull(name, "name").key();
    }

    /** How many failures are remembered, for a test that proves old ones are let go. */
    int remembered() {
        return until.size();
    }
}
