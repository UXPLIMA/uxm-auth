package com.uxplima.auth.domain.hash;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import com.uxplima.auth.domain.password.Password;

/**
 * PBKDF2-HMAC-SHA256 from the JDK, written as {@code pbkdf2-sha256$<iterations>$<salt>$<hash>}.
 *
 * <p>It needs no library at all, which is why an operator who distrusts a dependency may pick it.
 */
public final class Pbkdf2Hasher implements PasswordHasher {

    private static final Pattern FORMAT =
            Pattern.compile("^pbkdf2-sha256\\$(\\d+)\\$([A-Za-z0-9+/=]+)\\$([A-Za-z0-9+/=]+)$");
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final int iterations;

    public Pbkdf2Hasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("pbkdf2 needs at least one iteration, got " + iterations);
        }
        this.iterations = iterations;
    }

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.PBKDF2;
    }

    @Override
    public PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = Salts.next(SALT_BYTES);
        byte[] derived = derive(password, salt, iterations, HASH_BYTES);
        Base64.Encoder base64 = Base64.getEncoder();
        return new PasswordHash("pbkdf2-sha256$" + iterations + "$" + base64.encodeToString(salt) + "$"
                + base64.encodeToString(derived));
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(hash, "hash");
        Matcher parts = FORMAT.matcher(hash.encoded());
        if (!parts.matches()) {
            return false;
        }
        byte[] expected = Base64.getDecoder().decode(parts.group(3));
        byte[] actual = derive(
                password,
                Base64.getDecoder().decode(parts.group(2)),
                Integer.parseInt(parts.group(1)),
                expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        Matcher parts = FORMAT.matcher(hash.encoded());
        return parts.matches() && Integer.parseInt(parts.group(1)) == iterations;
    }

    private static byte[] derive(Password password, byte[] salt, int iterations, int length) {
        char[] chars = password.chars();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, length * 8);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("the JDK has no " + ALGORITHM, missing);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }
}
