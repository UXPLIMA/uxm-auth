package com.uxplima.auth.domain.hash;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;

/**
 * AuthMe's default: {@code $SHA$<salt>$<sha256(sha256(password) + salt)>}, in lower-case hex.
 *
 * <p>It is weak, because SHA-256 is fast and a leaked table is cracked on a graphics card. It is here because
 * every AuthMe database and every website that shares one holds it. The operator chooses it as the written
 * algorithm only for a website that cannot read anything else.
 */
public final class AuthMeSha256Hasher implements PasswordHasher {

    private static final Pattern FORMAT = Pattern.compile("^\\$SHA\\$([0-9A-Za-z]+)\\$([0-9a-fA-F]{64})$");
    private static final int SALT_BYTES = 8;

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.AUTHME_SHA256;
    }

    @Override
    public PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        String salt = HexFormat.of().formatHex(Salts.next(SALT_BYTES));
        return new PasswordHash("$SHA$" + salt + "$" + digest(password, salt));
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(hash, "hash");
        Matcher parts = FORMAT.matcher(hash.encoded());
        if (!parts.matches()) {
            return false;
        }
        byte[] expected = parts.group(2).toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = digest(password, parts.group(1)).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Objects.requireNonNull(hash, "hash");
        return FORMAT.matcher(hash.encoded()).matches();
    }

    private static String digest(Password password, String salt) {
        byte[] secret = password.utf8();
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String inner = HexFormat.of().formatHex(sha.digest(secret));
            return HexFormat.of().formatHex(sha.digest((inner + salt).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("the JDK has no SHA-256", missing);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
    }
}
