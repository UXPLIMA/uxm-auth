package com.uxplima.auth.domain.hash;

import java.util.Objects;

/**
 * A stored password hash in its encoded form.
 *
 * <p>It never prints itself. A hash in a log line is a hash an attacker can take offline, and a record's
 * generated {@code toString} would print it the first time anybody logged an account.
 */
public record PasswordHash(String encoded) {

    public PasswordHash {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.isEmpty()) {
            throw new IllegalArgumentException("a hash is never empty");
        }
    }

    @Override
    public String toString() {
        return "PasswordHash[hidden]";
    }
}
