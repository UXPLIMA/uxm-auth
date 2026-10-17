package com.uxplima.auth.domain.hash;

import java.util.Objects;

/** What checking a password against a stored hash found. */
public sealed interface Verification {

    /** Not the password, or a hash nothing here can read. */
    record Wrong() implements Verification {}

    /** The password, and the hash needs nothing. */
    record Right() implements Verification {}

    /**
     * The password, stored in an older algorithm or with older parameters. The fresh hash is in the current
     * one, made now because this is the only moment the clear text is available.
     */
    record RightButRehash(PasswordHash fresh) implements Verification {
        public RightButRehash {
            Objects.requireNonNull(fresh, "fresh");
        }
    }
}
