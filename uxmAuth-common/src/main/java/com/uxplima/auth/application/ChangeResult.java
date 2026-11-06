package com.uxplima.auth.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.auth.domain.password.PasswordVerdict;

/** What a password change came to. */
public sealed interface ChangeResult {

    record Changed() implements ChangeResult {}

    /** A weak password was replaced at the login it held up: the player is now logged in. */
    record Replaced() implements ChangeResult {}

    /** The old password was not the one stored. */
    record WrongOld() implements ChangeResult {}

    /** The wrong old password banned the address it came from, for this long; the platform sends the player away. */
    record Banned(Duration lasts) implements ChangeResult {
        public Banned {
            Objects.requireNonNull(lasts, "lasts");
        }
    }

    /** Too many wrong passwords and codes were typed for the account lately: no old one is checked for this long. */
    record Locked(Duration left) implements ChangeResult {
        public Locked {
            Objects.requireNonNull(left, "left");
        }
    }

    /** The policy refused the new password, for this reason. */
    record Rejected(PasswordVerdict verdict) implements ChangeResult {
        public Rejected {
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    record NotLoggedIn() implements ChangeResult {}

    /**
     * The password the login was held up by was changed elsewhere, by staff or on another server, before the new
     * one came: that change stands, nothing was written, and the player logs in again.
     */
    record ChangedElsewhere() implements ChangeResult {}

    /** The player left before the answer was ready. Nothing was written. */
    record Gone() implements ChangeResult {}
}
