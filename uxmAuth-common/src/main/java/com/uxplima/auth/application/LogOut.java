package com.uxplima.auth.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;

/** Sends a logged-in player back to the door, with every attempt given back. */
public final class LogOut {

    /** What a logout did. */
    public enum Result {
        /** The player waits at the door again. */
        LOGGED_OUT,
        /** The player was not logged in. */
        NOT_LOGGED_IN,
        /** The account has no password, as a Bedrock one, so no door could let it back in; it stays in. */
        NO_PASSWORD
    }

    private final Sessions sessions;
    private final IntSupplier maxAttempts;
    private final RememberedSessions remembered;

    /** @param remembered the sessions a logout ends, so the next join asks the password */
    public LogOut(Sessions sessions, IntSupplier maxAttempts, RememberedSessions remembered) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
        this.remembered = Objects.requireNonNull(remembered, "remembered");
    }

    /** Sends the player to the door, if they were in and have a password to come back with. */
    public Result run(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        AtomicReference<Result> result = new AtomicReference<>(Result.NOT_LOGGED_IN);
        sessions.update(connection, current -> {
            if (!(current instanceof AuthState.Authenticated authenticated)) {
                return current;
            }
            if (authenticated.account().password() == null) {
                result.set(Result.NO_PASSWORD);
                return current;
            }
            result.set(Result.LOGGED_OUT);
            return new AuthState.Registered(authenticated.account(), Attempts.fresh(maxAttempts.getAsInt()));
        });
        Result done = Objects.requireNonNull(result.get(), "result");
        if (done == Result.LOGGED_OUT) {
            sessions.state(connection)
                    .filter(AuthState.Registered.class::isInstance)
                    .map(state -> ((AuthState.Registered) state).account().id())
                    .ifPresent(remembered::forget);
        }
        return done;
    }
}
