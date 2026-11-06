package com.uxplima.auth.application;

import java.util.Objects;

import com.uxplima.auth.domain.password.PasswordVerdict;

/** What a password change came to. */
public sealed interface ChangeResult {

    record Changed() implements ChangeResult {}

    /** A weak password was replaced at the login it held up: the player is now logged in. */
    record Replaced() implements ChangeResult {}

    /** The old password was not the one stored. */
    record WrongOld() implements ChangeResult {}

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
