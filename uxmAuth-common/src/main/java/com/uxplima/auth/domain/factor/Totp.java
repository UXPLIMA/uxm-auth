package com.uxplima.auth.domain.factor;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.random.RandomGenerator;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Time-based codes as RFC 6238 makes them with HMAC-SHA1, which is what every authenticator app speaks. A code
 * is taken for its own step and a few steps either side of now, and only once: a step at or before the last one
 * used answers nothing.
 */
public final class Totp {

    /** The length of a new secret, in bytes: the 160 bits RFC 4226 recommends. */
    public static final int SECRET_BYTES = 20;

    private static final int[] POWERS = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private final int digits;
    private final long period;
    private final int window;

    /**
     * @param digits how long a code is, 6 to 8
     * @param period how long one code lasts, 15 seconds to 5 minutes
     * @param window how many steps either side of now are taken too, 0 to 4
     */
    public Totp(int digits, Duration period, int window) {
        Objects.requireNonNull(period, "period");
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("A code has 6 to 8 digits, got " + digits);
        }
        if (period.toSeconds() < 15 || period.toSeconds() > 300) {
            throw new IllegalArgumentException("A code's period is 15 to 300 seconds, got " + period.toSeconds());
        }
        if (window < 0 || window > 4) {
            throw new IllegalArgumentException("The window is 0 to 4 steps either side, got " + window);
        }
        this.digits = digits;
        this.period = period.toSeconds();
        this.window = window;
    }

    /** How long a code is. */
    public int digits() {
        return digits;
    }

    /** How long one code lasts, in seconds. */
    public long periodSeconds() {
        return period;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Totp that && digits == that.digits && period == that.period && window == that.window;
    }

    @Override
    public int hashCode() {
        return Objects.hash(digits, period, window);
    }

    @Override
    public String toString() {
        return "Totp[digits=" + digits + ", period=" + period + "s, window=" + window + "]";
    }

    /** A new secret from this generator; a server passes a secure one. */
    public static byte[] newSecret(RandomGenerator random) {
        byte[] secret = new byte[SECRET_BYTES];
        Objects.requireNonNull(random, "random").nextBytes(secret);
        return secret;
    }

    /** The step this moment falls in. */
    public long step(Instant now) {
        return Math.floorDiv(Objects.requireNonNull(now, "now").getEpochSecond(), period);
    }

    /** The code of this step. */
    public String code(byte[] secret, long step) {
        Objects.requireNonNull(secret, "secret");
        byte[] hash = hmac(secret, ByteBuffer.allocate(Long.BYTES).putLong(step).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        String code = Integer.toString(binary % POWERS[digits]);
        return "0".repeat(digits - code.length()) + code;
    }

    /**
     * The step a typed code matches, now or inside the window, when that step is past the last one used;
     * empty otherwise. Every step of the window is compared, so the time taken says nothing about which one
     * matched.
     */
    public OptionalLong verify(byte[] secret, String typed, Instant now, long lastUsed) {
        Objects.requireNonNull(secret, "secret");
        String code = Objects.requireNonNull(typed, "typed").replace(" ", "");
        // Only the length is checked first: a code with a letter in it equals no code made of digits.
        if (code.length() != digits) {
            return OptionalLong.empty();
        }
        byte[] wanted = code.getBytes(StandardCharsets.US_ASCII);
        long current = step(now);
        long matched = Long.MIN_VALUE;
        for (long step = current - window; step <= current + window; step++) {
            byte[] made = code(secret, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(made, wanted) && step > lastUsed) {
                matched = step;
            }
        }
        return matched == Long.MIN_VALUE ? OptionalLong.empty() : OptionalLong.of(matched);
    }

    private static byte[] hmac(byte[] secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("HmacSHA1 is part of every Java runtime", missing);
        }
    }
}
