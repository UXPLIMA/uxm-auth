package com.uxplima.auth.application;

import java.util.Objects;

import com.uxplima.auth.domain.account.Account;

/** What a login came to. */
public sealed interface LoginResult {

    record LoggedIn(Account account) implements LoginResult {
        public LoggedIn {
            Objects.requireNonNull(account, "account");
        }
    }

    /** A wrong password, with this many tries left. */
    record Wrong(int attemptsLeft) implements LoginResult {}

    /** The last try is spent. The platform decides what follows, usually a kick. */
    record TooManyAttempts() implements LoginResult {}

    /** There is no password to log in with: no account, or one that never set a password. */
    record NotRegistered() implements LoginResult {}

    record AlreadyLoggedIn() implements LoginResult {}

    /** The player left before the answer was ready. Nothing was written. */
    record Gone() implements LoginResult {}
}
