package com.uxplima.auth.paper.adapter.inbound.hold;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;

/**
 * How long a waiting player has left before the timeout kicks them: the register limit while they have no account,
 * the login limit otherwise. The effect lines read it, so a boss bar or a title can count down.
 */
public final class WaitingTimes {

    private final Sessions sessions;
    private final Supplier<AuthSettings.Timeouts> timeouts;
    private final Clock clock;

    /** When each waiting player arrived; changed only through compute. */
    private final Map<UUID, Instant> arrivals = new ConcurrentHashMap<>();

    public WaitingTimes(Sessions sessions, Supplier<AuthSettings.Timeouts> timeouts, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.timeouts = Objects.requireNonNull(timeouts, "timeouts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * The player started to wait at this moment.
     *
     * <p>Every player whose connection closed is let go here too. On Folia the task of an entity that is gone
     * never runs again, so the beat that forgets a player who left while waiting does not come.
     */
    void arrived(UUID id, Instant at) {
        Objects.requireNonNull(at, "at");
        for (UUID other : arrivals.keySet()) {
            arrivals.compute(other, (key, when) -> sessions.state(key).isPresent() ? when : null);
        }
        arrivals.compute(Objects.requireNonNull(id, "id"), (key, before) -> at);
    }

    /** The player no longer waits: they are in, or gone. */
    void gone(UUID id) {
        arrivals.compute(Objects.requireNonNull(id, "id"), (key, before) -> null);
    }

    /** How many players are remembered, for a test that proves they are let go. */
    int waiting() {
        return arrivals.size();
    }

    /** The time this player has left, never below none; empty for a player who does not wait. */
    public Optional<Duration> left(UUID id) {
        Instant arrived = arrivals.get(Objects.requireNonNull(id, "id"));
        AuthState state = sessions.state(id).orElse(null);
        if (arrived == null
                || state == null
                || state instanceof AuthState.Authenticated
                || state instanceof AuthState.SettingUp) {
            return Optional.empty();
        }
        AuthSettings.Timeouts limits = timeouts.get();
        Duration limit = state instanceof AuthState.Unregistered ? limits.register() : limits.login();
        Duration left = limit.minus(Duration.between(arrived, clock.instant()));
        return Optional.of(left.isNegative() ? Duration.ZERO : left);
    }
}
