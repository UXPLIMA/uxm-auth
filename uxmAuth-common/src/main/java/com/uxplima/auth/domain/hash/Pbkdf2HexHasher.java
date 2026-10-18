package com.uxplima.auth.domain.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.uxplima.auth.domain.password.Password;

/**
 * PBKDF2-HMAC-SHA256 as a website's login table holds it when it is set to pbkdf2:
 * {@code pbkdf2_sha256$<iterations>$<salt>$<hash>}, where the salt is sixteen hex digits used as text and the hash
 * is the 64-byte key in upper-case hex. Written only for that table; the plugin's own pbkdf2 is the other form.
 */
public final class Pbkdf2HexHasher implements PasswordHasher {

    private static final Pattern FORMAT = Pattern.compile("^pbkdf2_sha256\\$(\\d+)\\$([^$]+)\\$([0-9A-Fa-f]+)$");
    private static final int SALT_BYTES = 8;
    private static final int KEY_BYTES = 64;

    private final int iterations;

    public Pbkdf2HexHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("pbkdf2 needs at least one iteration, got " + iterations);
        }
        this.iterations = iterations;
    }

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.PBKDF2_HEX;
    }

    @Override
    public PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        String salt = HexFormat.of().formatHex(Salts.next(SALT_BYTES));
        byte[] key = derive(password, salt, iterations, KEY_BYTES);
        return new PasswordHash("pbkdf2_sha256$" + iterations + "$" + salt + "$"
                + HexFormat.of().withUpperCase().formatHex(key));
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        if (!parts.matches() || parts.group(3).length() % 2 != 0) {
            return false;
        }
        byte[] expected = HexFormat.of().parseHex(parts.group(3));
        byte[] actual = derive(password, parts.group(2), Integer.parseInt(parts.group(1)), expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        return parts.matches() && Integer.parseInt(parts.group(1)) == iterations;
    }

    private static byte[] derive(Password password, String salt, int iterations, int length) {
        char[] chars = password.chars();
        PBEKeySpec spec = new PBEKeySpec(chars, salt.getBytes(StandardCharsets.US_ASCII), iterations, length * 8);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException missing) {
            throw new IllegalStateException("the JDK has no PBKDF2WithHmacSHA256", missing);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
