package com.uxplima.auth.domain.hash;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;
import org.bouncycastle.crypto.generators.SCrypt;

/**
 * scrypt as Blysis writes it when its hashing setting is scrypt:
 * {@code $scrypt$N=<cost>,r=<block>,p=<parallel>$<salt>$<key>}, the salt and the key in padded Base64 and the
 * password taken as its UTF-8 bytes. The parameters are read from each hash, so one written under other parameters
 * still verifies; new hashes are written with Blysis' own.
 */
public final class ScryptHasher implements PasswordHasher {

    private static final Pattern FORMAT = Pattern.compile(
            "^\\$scrypt\\$N=(\\d{1,10}),r=(\\d{1,10}),p=(\\d{1,10})\\$([A-Za-z0-9+/=]+)\\$([A-Za-z0-9+/=]+)$");
    private static final int COST = 1 << 16;
    private static final int BLOCK = 8;
    private static final int PARALLEL = 1;
    private static final int KEY_BYTES = 32;
    private static final int SALT_BYTES = 16;
    private static final int LONGEST_KEY = 1024;
    /**
     * The most memory one check may take, which is what Node allows Blysis: a hash that claims more is not a match,
     * there or here, so a row cannot make a login take the memory of the server.
     */
    private static final long MOST_MEMORY = 192L * 1024 * 1024;

    @Override
    public HashAlgorithm algorithm() {
        return HashAlgorithm.SCRYPT;
    }

    @Override
    public PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = Salts.next(SALT_BYTES);
        byte[] key = derive(password, salt, COST, BLOCK, PARALLEL, KEY_BYTES);
        Base64.Encoder base64 = Base64.getEncoder();
        return new PasswordHash("$scrypt$N=" + COST + ",r=" + BLOCK + ",p=" + PARALLEL + "$"
                + base64.encodeToString(salt) + "$" + base64.encodeToString(key));
    }

    @Override
    public boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        if (!parts.matches()) {
            return false;
        }
        long cost = Long.parseLong(parts.group(1));
        long block = Long.parseLong(parts.group(2));
        long parallel = Long.parseLong(parts.group(3));
        if (!runnable(cost, block, parallel)) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts.group(4));
            expected = Base64.getDecoder().decode(parts.group(5));
        } catch (IllegalArgumentException notBase64) {
            return false;
        }
        if (salt.length == 0 || expected.length == 0 || expected.length > LONGEST_KEY) {
            return false;
        }
        byte[] actual = derive(password, salt, (int) cost, (int) block, (int) parallel, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    @Override
    public boolean isCurrent(PasswordHash hash) {
        Matcher parts = FORMAT.matcher(Objects.requireNonNull(hash, "hash").encoded());
        return parts.matches()
                && parts.group(1).equals(String.valueOf(COST))
                && parts.group(2).equals(String.valueOf(BLOCK))
                && parts.group(3).equals(String.valueOf(PARALLEL));
    }

    /** Parameters scrypt accepts, within the memory Node gives Blysis for one check. */
    private static boolean runnable(long cost, long block, long parallel) {
        boolean powerOfTwo = cost > 1 && (cost & (cost - 1)) == 0;
        return powerOfTwo
                && block > 0
                && parallel > 0
                && cost <= Integer.MAX_VALUE
                && block * parallel < (1 << 30)
                && 128L * cost * block <= MOST_MEMORY;
    }

    private static byte[] derive(Password password, byte[] salt, int cost, int block, int parallel, int length) {
        byte[] bytes = password.utf8();
        try {
            return SCrypt.generate(bytes, salt, cost, block, parallel, length);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }
}
