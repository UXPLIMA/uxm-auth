package com.uxplima.auth.domain.hash;

import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.password.Password;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/**
 * Argon2 in the PHC string form every Argon2 library writes:
 * {@code $<variant>$v=19$m=<KiB>,t=<iterations>,p=<lanes>$<salt>$<hash>}, base64 without padding. One variant at a
 * time: an argon2id hash is never read as argon2i, nor the other way round.
 */
final class Argon2Phc {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;

    private final String variant;
    private final int type;
    private final Pattern format;
    private final int memoryKib;
    private final int iterations;
    private final int parallelism;

    /** @param type the BouncyCastle type of the variant */
    Argon2Phc(String variant, int type, int memoryKib, int iterations, int parallelism) {
        if (memoryKib < 8 * parallelism || iterations < 1 || parallelism < 1) {
            throw new IllegalArgumentException(variant + " needs at least 8 KiB per lane, one iteration and one lane");
        }
        this.variant = variant;
        this.type = type;
        this.format = Pattern.compile(
                "^\\$" + variant + "\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$([A-Za-z0-9+/]+)\\$([A-Za-z0-9+/]+)$");
        this.memoryKib = memoryKib;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    PasswordHash hash(Password password) {
        Objects.requireNonNull(password, "password");
        byte[] salt = Salts.next(SALT_BYTES);
        byte[] derived = derive(password, salt, memoryKib, iterations, parallelism, HASH_BYTES);
        Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
        return new PasswordHash("$" + variant + "$v=19$m=" + memoryKib + ",t=" + iterations + ",p=" + parallelism + "$"
                + base64.encodeToString(salt) + "$" + base64.encodeToString(derived));
    }

    boolean verify(Password password, PasswordHash hash) {
        Objects.requireNonNull(password, "password");
        Matcher parts = format.matcher(Objects.requireNonNull(hash, "hash").encoded());
        if (!parts.matches()) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts.group(4));
        byte[] expected = Base64.getDecoder().decode(parts.group(5));
        byte[] actual = derive(
                password,
                salt,
                Integer.parseInt(parts.group(1)),
                Integer.parseInt(parts.group(2)),
                Integer.parseInt(parts.group(3)),
                expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    boolean isCurrent(PasswordHash hash) {
        Matcher parts = format.matcher(Objects.requireNonNull(hash, "hash").encoded());
        return parts.matches()
                && Integer.parseInt(parts.group(1)) == memoryKib
                && Integer.parseInt(parts.group(2)) == iterations
                && Integer.parseInt(parts.group(3)) == parallelism;
    }

    private byte[] derive(Password password, byte[] salt, int memory, int passes, int lanes, int length) {
        Argon2BytesGenerator generator = new Argon2BytesGenerator();
        generator.init(new Argon2Parameters.Builder(type)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withMemoryAsKB(memory)
                .withIterations(passes)
                .withParallelism(lanes)
                .withSalt(salt)
                .build());
        byte[] secret = password.utf8();
        byte[] out = new byte[length];
        try {
            generator.generateBytes(secret, out);
        } finally {
            Arrays.fill(secret, (byte) 0);
        }
        return out;
    }
}
