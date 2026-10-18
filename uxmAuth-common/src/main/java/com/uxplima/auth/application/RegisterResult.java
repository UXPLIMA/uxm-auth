package com.uxplima.auth.application;

import java.util.Objects;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.PasswordVerdict;

/** What a registration came to. */
public sealed interface RegisterResult {

    /** The account exists and the player is logged in. */
    record Created(Account account) implements RegisterResult {
        public Created {
            Objects.requireNonNull(account, "account");
        }
    }

    /** The password and its confirmation differ. */
    record Mismatch() implements RegisterResult {}

    /** The policy refused the password, for this reason. */
    record Rejected(PasswordVerdict verdict) implements RegisterResult {
        public Rejected {
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    /** Somebody holds the name, written as they wrote it. */
    record NameTaken(PlayerName stored) implements RegisterResult {
        public NameTaken {
            Objects.requireNonNull(stored, "stored");
        }
    }

    /** The address made all the password accounts it may for now. */
    record TooManyAccounts() implements RegisterResult {}

    /** A captcha stands before this registration: the connection must solve the one it is shown first. */
    record CaptchaNeeded() implements RegisterResult {}

    /** This connection already has an account. */
    record AlreadyRegistered() implements RegisterResult {}

    /** The player left before the answer was ready. Nothing was written. */
    record Gone() implements RegisterResult {}

    /** Another plugin refused the registration before the account was made, for this reason, which may be empty. */
    record Refused(String reason) implements RegisterResult {
        public Refused {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
