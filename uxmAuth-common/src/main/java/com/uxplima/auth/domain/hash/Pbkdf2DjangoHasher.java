package com.uxplima.auth.domain.hash;

import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;

/**
 * Django's PBKDF2: {@code pbkdf2_sha256$<rounds>$<salt>$<base64 of a 32-byte key>}, the salt taken as its text.
 * Some login plugins and the websites built on Django wrote it. Read only: a right password moves to the current
 * algorithm.
 */
public final class Pbkdf2DjangoHasher implements PasswordHasher {

    private static final Pattern FORMAT =
            Pattern.compile("^pbkdf2_sha256\\$(\\d{1,8})\\$([^$]+)\\$([A-Za-z0-9+/]+=*)$");

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.PBKDF2_DJANGO;
    }

    /** Never: a Django hash is read, and replaced at the next login. */
    @Override
    public PasswordHash hash(Password password) {
        throw new UnsupportedOperationException("a Django hash is read, never written");
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        if (!parts.matches()) {
            return false;
        }
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(parts.group(3));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
        int rounds = Integer.parseInt(parts.group(1));
        if (rounds == 0 || expected.length == 0) {
            return false;
        }
        byte[] actual = Pbkdf2HexHasher.derive(password, parts.group(2), rounds, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        return false;
    }
}
