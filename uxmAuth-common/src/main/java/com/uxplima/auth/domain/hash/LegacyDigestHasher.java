package com.uxplima.auth.domain.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;

/**
 * The fast digests other login plugins stored, read through the one form an import rewrites them into:
 * {@code $digest$<md5|sha256|sha512>$<mode>$<salt>$<hex>}.
 *
 * <p>The modes are the four the sources use: {@code double-salted} is {@code H(hex(H(password)) + salt)},
 * {@code salted} is {@code H(password + salt)}, {@code plain} is {@code H(password)} and {@code twice} is
 * {@code H(hex(H(password)))}. Every one of them is weak, which is why this hasher only reads: a right password
 * is rehashed with the current algorithm at once.
 */
public final class LegacyDigestHasher implements PasswordHasher {

    /** What every rewritten digest starts with. */
    static final String PREFIX = "$digest$";

    private static final Pattern FORMAT = Pattern.compile(
            "^\\$digest\\$(md5|sha256|sha512)\\$(double-salted|salted|plain|twice)\\$([^$]*)\\$([0-9a-f]+)$");

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.LEGACY_DIGEST;
    }

    /** Never: a legacy digest is read, and replaced at the next login. */
    @Override
    public PasswordHash hash(Password password) {
        throw new UnsupportedOperationException("a legacy digest is read, never written");
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        if (!parts.matches()) {
            return false;
        }
        byte[] expected = parts.group(4).getBytes(StandardCharsets.US_ASCII);
        byte[] actual =
                digest(parts.group(1), parts.group(2), password, parts.group(3)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        return false;
    }

    /** The form an import writes, from parts {@link #readable} accepted. */
    static PasswordHash encode(String algorithm, String mode, String salt, String hex) {
        return new PasswordHash(PREFIX + algorithm + "$" + mode + "$" + salt + "$" + hex.toLowerCase(Locale.ROOT));
    }

    /** Whether these parts make a digest this hasher reads. */
    static boolean readable(String algorithm, String mode, String salt, String hex) {
        return FORMAT.matcher(PREFIX + algorithm + "$" + mode + "$" + salt + "$" + hex.toLowerCase(Locale.ROOT))
                        .matches()
                && hex.length() == length(algorithm);
    }

    /** The length in hex of a digest of this algorithm. */
    static int length(String algorithm) {
        return switch (algorithm) {
            case "md5" -> 32;
            case "sha256" -> 64;
            default -> 128;
        };
    }

    private static String digest(String algorithm, String mode, Password password, String salt) {
        byte[] secret = password.utf8();
        try {
            MessageDigest digest = MessageDigest.getInstance(javaName(algorithm));
            byte[] saltBytes = salt.getBytes(StandardCharsets.UTF_8);
            byte[] once = digest.digest(secret);
            byte[] input =
                    switch (mode) {
                        case "double-salted" -> concat(hex(once).getBytes(StandardCharsets.UTF_8), saltBytes);
                        case "salted" -> concat(secret, saltBytes);
                        case "twice" -> hex(once).getBytes(StandardCharsets.UTF_8);
                        default -> null;
                    };
            return hex(input == null ? once : digest.digest(input));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("the JDK has no " + algorithm, missing);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }

    private static String javaName(String algorithm) {
        return switch (algorithm) {
            case "md5" -> "MD5";
            case "sha256" -> "SHA-256";
            default -> "SHA-512";
        };
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] both = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, both, first.length, second.length);
        return both;
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }
}
