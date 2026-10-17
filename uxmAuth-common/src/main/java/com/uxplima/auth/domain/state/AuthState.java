package com.uxplima.auth.domain.state;

import java.util.Objects;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;

/** Where one connection stands. Only {@link Authenticated} is let past the waiting room. */
public sealed interface AuthState {

    /** Nobody has an account under this name yet. */
    record Unregistered(PlayerName name) implements AuthState {
        public Unregistered {
            Objects.requireNonNull(name, "name");
        }
    }

    /** The account exists and waits for its password. */
    record Registered(Account account, Attempts attempts) implements AuthState {
        public Registered {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(attempts, "attempts");
        }
    }

    /** Logged in. */
    record Authenticated(Account account, AuthMethod method) implements AuthState {
        public Authenticated {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(method, "method");
        }
    }
}
