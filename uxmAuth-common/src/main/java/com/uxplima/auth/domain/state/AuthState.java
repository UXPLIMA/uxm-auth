package com.uxplima.auth.domain.state;

import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.FactorKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.PasswordVerdict;
import org.jspecify.annotations.Nullable;

/** Where one connection stands. Only {@link Authenticated} is let past the waiting room. */
public sealed interface AuthState {

    /** Nobody has an account under this name yet. */
    record Unregistered(PlayerName name, boolean unverified) implements AuthState {
        public Unregistered {
            Objects.requireNonNull(name, "name");
        }

        /** A name nobody doubts: a source said who owns it, or nobody had to be asked. */
        public Unregistered(PlayerName name) {
            this(name, false);
        }
    }

    /** The account exists and waits for its password. */
    record Registered(Account account, Attempts attempts) implements AuthState {
        public Registered {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(attempts, "attempts");
        }
    }

    /**
     * The password was right, but today's rules refuse it: the player stays held until they set a new one, and
     * reads why.
     */
    record Replacing(Account account, PasswordVerdict why) implements AuthState {
        public Replacing {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(why, "why");
        }
    }

    /**
     * The first step passed, by this way, and a code is asked before the login is whole. A weak password that
     * passed waits behind the code to be replaced.
     *
     * @param kinds the second factors the account has, any of which answers
     * @param mustReplace why the password no longer passes, when it does not
     */
    record SecondFactor(
            Account account,
            AuthMethod method,
            Set<FactorKind> kinds,
            Attempts attempts,
            @Nullable PasswordVerdict mustReplace)
            implements AuthState {
        public SecondFactor {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(method, "method");
            kinds = Set.copyOf(Objects.requireNonNull(kinds, "kinds"));
            Objects.requireNonNull(attempts, "attempts");
            if (kinds.isEmpty()) {
                throw new IllegalArgumentException("a second step needs a factor to answer it");
            }
        }

        /** The same step with one more wrong code. */
        public SecondFactor failed() {
            return new SecondFactor(account, method, kinds, attempts.failed(), mustReplace);
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
