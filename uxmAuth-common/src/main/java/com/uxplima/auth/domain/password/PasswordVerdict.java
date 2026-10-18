package com.uxplima.auth.domain.password;

import java.util.Objects;
import java.util.Set;

/** What the password policy said about a password. */
public sealed interface PasswordVerdict {

    /** The password may be used. */
    record Accepted() implements PasswordVerdict {}

    /** Fewer characters than the minimum. */
    record TooShort(int minimum) implements PasswordVerdict {}

    /** More characters than the maximum. */
    record TooLong(int maximum) implements PasswordVerdict {}

    /**
     * More bytes than the hash reads. BCrypt ignores everything past 72 bytes, and a Turkish letter takes two,
     * so a password can be short enough to see and still too long to be checked in full.
     */
    record TooLongForTheHash(int maximumBytes) implements PasswordVerdict {}

    /** A character the operator's pattern does not allow. */
    record BadCharacters() implements PasswordVerdict {}

    /** The password is on the operator's list of common ones. */
    record Common() implements PasswordVerdict {}

    /** The password is known from a breach, as haveibeenpwned counts it. */
    record Breached() implements PasswordVerdict {}

    /** A required kind of character is missing. */
    record Missing(Set<CharClass> classes) implements PasswordVerdict {
        public Missing {
            classes = Set.copyOf(Objects.requireNonNull(classes, "classes"));
        }
    }
}
