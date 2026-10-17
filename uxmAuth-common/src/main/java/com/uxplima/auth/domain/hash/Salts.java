package com.uxplima.auth.domain.hash;

import java.security.SecureRandom;

/** Random salts. One generator, because a {@link SecureRandom} is costly to seed and safe to share. */
final class Salts {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Salts() {}

    static byte[] next(int length) {
        byte[] salt = new byte[length];
        RANDOM.nextBytes(salt);
        return salt;
    }
}
