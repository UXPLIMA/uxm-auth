package com.uxplima.auth.application;

import java.util.Objects;

import com.uxplima.auth.domain.password.PasswordVerdict;

/** What a password change came to. */
public sealed interface ChangeResult {

    record Changed() implements ChangeResult {}

    /** The old password was not the one stored. */
    record WrongOld() implements ChangeResult {}

    /** The policy refused the new password, for this reason. */
    record Rejected(PasswordVerdict verdict) implements ChangeResult {
        public Rejected {
            Objects.requireNonNull(verdict, "verdict");
        }
    }

    record NotLoggedIn() implements ChangeResult {}

    /** The player left before the answer was ready. Nothing was written. */
    record Gone() implements ChangeResult {}
}
