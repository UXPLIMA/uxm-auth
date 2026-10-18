package com.uxplima.auth.domain.hash;

import java.util.Objects;
import java.util.Optional;

/** Which algorithm wrote a stored hash, read from the prefix each one writes. */
public final class HashFormats {

    private HashFormats() {}

    public static Optional<HashAlgorithm> recognise(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        String encoded = hash.encoded();
        if (encoded.startsWith("$argon2id$")) {
            return Optional.of(HashAlgorithm.ARGON2ID);
        }
        if (encoded.startsWith("$2a$") || encoded.startsWith("$2b$") || encoded.startsWith("$2y$")) {
            return Optional.of(HashAlgorithm.BCRYPT);
        }
        if (encoded.startsWith("pbkdf2-sha256$")) {
            return Optional.of(HashAlgorithm.PBKDF2);
        }
        if (encoded.startsWith("$argon2i$")) {
            return Optional.of(HashAlgorithm.ARGON2I);
        }
        if (encoded.startsWith("pbkdf2_sha256$")) {
            return Optional.of(HashAlgorithm.PBKDF2_HEX);
        }
        if (encoded.startsWith("$SHA$")) {
            return Optional.of(HashAlgorithm.AUTHME_SHA256);
        }
        return Optional.empty();
    }
}
