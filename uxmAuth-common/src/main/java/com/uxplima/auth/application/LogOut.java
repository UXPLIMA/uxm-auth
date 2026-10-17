package com.uxplima.auth.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntSupplier;

import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;

/** Sends a logged-in player back to the door, with every attempt given back. */
public final class LogOut {

    private final Sessions sessions;
    private final IntSupplier maxAttempts;

    public LogOut(Sessions sessions, IntSupplier maxAttempts) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
    }

    /** Whether the player was logged in. A player who was not is left as they were. */
    public boolean run(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        AtomicBoolean loggedOut = new AtomicBoolean();
        sessions.update(connection, current -> {
            if (current instanceof AuthState.Authenticated authenticated) {
                loggedOut.set(true);
                return new AuthState.Registered(authenticated.account(), Attempts.fresh(maxAttempts.getAsInt()));
            }
            return current;
        });
        return loggedOut.get();
    }
}
