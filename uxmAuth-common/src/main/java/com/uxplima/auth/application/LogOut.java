package com.uxplima.auth.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntSupplier;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Sends a logged-in player back to the door, with every attempt given back, and a connection whose account's
 * sessions ended back to the password.
 */
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

    /** What ending a connection's sessions did. */
    public enum Ended {
        /** The player was in and waits at the door again. */
        LOGGED_OUT,
        /**
         * The connection had passed the password and waited for more: a new password, a code or the app. It
         * waits at the password again.
         */
        BACK_TO_PASSWORD,
        /** The connection had not passed the password, so the next try is checked against the new one anyway. */
        UNTOUCHED,
        /** The account has no password to send the connection back to; it stays as it is. */
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

    /**
     * The account's sessions ended, as its password changed: the connection goes back to the password, with every
     * attempt given back, from wherever past it it stood. A connection asked for a new password, a code or the app
     * had proved the old password only, and must not finish on it. The remembered sessions are not touched here;
     * whoever ended the sessions forgot them already.
     */
    public Ended end(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        AtomicReference<Ended> result = new AtomicReference<>(Ended.UNTOUCHED);
        sessions.update(connection, current -> {
            Account past = pastThePassword(current);
            if (past == null) {
                result.set(Ended.UNTOUCHED);
                return current;
            }
            if (past.password() == null) {
                result.set(Ended.NO_PASSWORD);
                return current;
            }
            result.set(current instanceof AuthState.Authenticated ? Ended.LOGGED_OUT : Ended.BACK_TO_PASSWORD);
            return new AuthState.Registered(past, Attempts.fresh(maxAttempts.getAsInt()));
        });
        return Objects.requireNonNull(result.get(), "result");
    }

    /** The account of a state that stands past the password, or null for one that waits for it or has none. */
    private static @Nullable Account pastThePassword(AuthState state) {
        return switch (state) {
            case AuthState.Authenticated in -> in.account();
            case AuthState.Replacing replacing -> replacing.account();
            case AuthState.SecondFactor second -> second.account();
            case AuthState.SettingUp setting -> setting.account();
            case AuthState.Registered waiting -> null;
            case AuthState.Unregistered unregistered -> null;
        };
    }
}
