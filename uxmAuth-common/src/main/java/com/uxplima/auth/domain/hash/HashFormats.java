package com.uxplima.auth.domain.hash;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Which algorithm wrote a stored hash, read from the prefix each one writes. */
public final class HashFormats {

    private static final Pattern HEX_KEY = Pattern.compile("\\$[0-9A-Fa-f]+$");

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
            // The websites write the key in hex, Django in base64; the key alone tells them apart.
            return Optional.of(
                    HEX_KEY.matcher(encoded).find() ? HashAlgorithm.PBKDF2_HEX : HashAlgorithm.PBKDF2_DJANGO);
        }
        if (encoded.startsWith(LegacyDigestHasher.PREFIX)) {
            return Optional.of(HashAlgorithm.LEGACY_DIGEST);
        }
        if (encoded.startsWith("$SHA$")) {
            return Optional.of(HashAlgorithm.AUTHME_SHA256);
        }
        return Optional.empty();
    }
}
