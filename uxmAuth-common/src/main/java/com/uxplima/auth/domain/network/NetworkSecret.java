package com.uxplima.auth.domain.network;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * The key the proxy signs with and every server checks with. The proxy makes one on its first start; the
 * operator copies the text to each server. Whoever holds it can log anybody in, so it is never printed.
 *
 * @param encoded the text the operator copies, whose bytes are the key
 */
public record NetworkSecret(String encoded) {

    /** Shorter than this, a secret could be guessed. A generated one is 43. */
    private static final int SHORTEST = 32;

    private static final int RANDOM_BYTES = 32;

    public NetworkSecret {
        Objects.requireNonNull(encoded, "encoded");
        if (encoded.length() < SHORTEST) {
            throw new IllegalArgumentException("a network secret is at least " + SHORTEST + " characters, got "
                    + encoded.length() + ". Copy the one the proxy wrote.");
        }
    }

    /** A new secret from 32 random bytes, written as 43 characters of URL-safe Base64. */
    public static NetworkSecret generate() {
        byte[] bytes = new byte[RANDOM_BYTES];
        new SecureRandom().nextBytes(bytes);
        return new NetworkSecret(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }

    /** The secret an operator wrote, without the spaces around it. */
    public static NetworkSecret parse(String written) {
        return new NetworkSecret(Objects.requireNonNull(written, "written").trim());
    }

    byte[] key() {
        return encoded.getBytes(StandardCharsets.UTF_8);
    }

    /** Never the secret itself. */
    @Override
    public String toString() {
        return "NetworkSecret[hidden]";
    }
}
