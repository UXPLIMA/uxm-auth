package com.uxplima.auth.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.password.PasswordVerdict;

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

    /** A wrong password from an address that is banned now, for this long; the platform sends the player away. */
    record Banned(Duration lasts) implements LoginResult {
        public Banned {
            Objects.requireNonNull(lasts, "lasts");
        }
    }

    /** The password was right, but today's rules refuse it, for this reason: a new one must be set first. */
    record MustReplace(PasswordVerdict why) implements LoginResult {
        public MustReplace {
            Objects.requireNonNull(why, "why");
        }
    }

    /** The password was right, and a code from the app is asked before the login is whole. */
    record CodeNeeded() implements LoginResult {}

    /** A captcha stands before this login: the connection must solve the one it is shown first. No try was spent. */
    record CaptchaNeeded() implements LoginResult {}

    /** There is no password to log in with: no account, or one that never set a password. */
    record NotRegistered() implements LoginResult {}

    record AlreadyLoggedIn() implements LoginResult {}

    /** The player left before the answer was ready. Nothing was written. */
    record Gone() implements LoginResult {}

    /** Another plugin refused the login before the password was checked, for this reason, which may be empty. */
    record Refused(String reason) implements LoginResult {
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
