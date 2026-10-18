package com.uxplima.auth.adapter.outbound;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Seals a second-factor secret with AES-GCM before it is stored, so a copy of the database alone hands nobody a
 * player's codes. A server alone keeps its key in a file of the data folder, made once; the members of a network
 * derive one key from the secret they already share, so a code set up on one server is read on every other.
 */
public final class SecretSeal {

    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte[] PURPOSE = "uxmauth second factor".getBytes(StandardCharsets.US_ASCII);

    /** A sealed text that does not open under this key: another key sealed it, or it was changed. */
    public static final class Unreadable extends RuntimeException {
        private static final long serialVersionUID = 1L;

        Unreadable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final SecretKeySpec key;
    private final List<SecretKeySpec> older;
    private final SecureRandom nonces = new SecureRandom();

    private SecretSeal(byte[] key) {
        this(new SecretKeySpec(key, "AES"), List.of());
    }

    private SecretSeal(SecretKeySpec key, List<SecretKeySpec> older) {
        this.key = key;
        this.older = List.copyOf(older);
    }

    /**
     * This key, which also opens what the older one sealed, for a server alone that joined a network: what it
     * sealed under its own file still opens, and everything sealed from now on is the network's.
     */
    public SecretSeal openingAlso(SecretSeal older) {
        Objects.requireNonNull(older, "older");
        List<SecretKeySpec> keys = new ArrayList<>(this.older);
        keys.add(older.key);
        keys.addAll(older.older);
        return new SecretSeal(key, keys);
    }

    /** The key in this file, where there is one; nothing is made. */
    public static Optional<SecretSeal> readKeyFile(Path file) {
        Objects.requireNonNull(file, "file");
        return Files.isRegularFile(file)
                ? Optional.of(fromKeyFile(file, RandomGenerator.getDefault()))
                : Optional.empty();
    }

    /** The key of a network, derived from the secret every member shares. */
    public static SecretSeal fromPassphrase(String passphrase) {
        Objects.requireNonNull(passphrase, "passphrase");
        if (passphrase.isBlank()) {
            throw new IllegalArgumentException("A second-factor key needs a secret that is not empty");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(passphrase.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return new SecretSeal(mac.doFinal(PURPOSE));
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("HmacSHA256 is part of every Java runtime", missing);
        }
    }

    /** The key of a server alone, read from this file, or made from this generator and written there first. */
    public static SecretSeal fromKeyFile(Path file, RandomGenerator random) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(random, "random");
        try {
            if (!Files.isRegularFile(file)) {
                byte[] made = new byte[KEY_BYTES];
                random.nextBytes(made);
                createOwnerOnly(file);
                Files.writeString(file, Base64.getEncoder().encodeToString(made));
            }
            byte[] key = Base64.getDecoder().decode(Files.readString(file).strip());
            if (key.length != KEY_BYTES) {
                throw new IllegalStateException(file + " holds no second-factor key of " + KEY_BYTES + " bytes");
            }
            return new SecretSeal(key);
        } catch (IOException failed) {
            throw new UncheckedIOException("The second-factor key in " + file + " could not be read", failed);
        }
    }

    /**
     * Makes the empty key file so that only the server's own account reads it from its first moment, where the
     * file system keeps owners apart.
     */
    private static void createOwnerOnly(Path file) throws IOException {
        try {
            Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        } catch (UnsupportedOperationException notPosix) {
            // Windows keeps no POSIX bits; the folder's own access list guards the file there.
            Files.createFile(file);
        }
    }

    /** The secret sealed under a fresh nonce, as text a column holds. */
    public String seal(byte[] secret) {
        Objects.requireNonNull(secret, "secret");
        byte[] nonce = new byte[NONCE_BYTES];
        nonces.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(secret);
            return Base64.getEncoder()
                    .encodeToString(ByteBuffer.allocate(nonce.length + sealed.length)
                            .put(nonce)
                            .put(sealed)
                            .array());
        } catch (GeneralSecurityException missing) {
            throw new IllegalStateException("AES-GCM is part of every Java runtime", missing);
        }
    }

    /** The secret a sealed text holds, when this key or an older one it opens sealed it and nobody changed it. */
    public byte[] open(String sealed) {
        Objects.requireNonNull(sealed, "sealed");
        try {
            return open(sealed, key);
        } catch (Unreadable notThisKey) {
            for (SecretKeySpec before : older) {
                try {
                    return open(sealed, before);
                } catch (Unreadable notThisOneEither) {
                    notThisKey.addSuppressed(notThisOneEither);
                }
            }
            throw notThisKey;
        }
    }

    private static byte[] open(String sealed, SecretKeySpec under) {
        try {
            byte[] all = Base64.getDecoder().decode(sealed);
            if (all.length <= NONCE_BYTES) {
                throw new IllegalArgumentException("too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, under, new GCMParameterSpec(TAG_BITS, all, 0, NONCE_BYTES));
            return cipher.doFinal(all, NONCE_BYTES, all.length - NONCE_BYTES);
        } catch (GeneralSecurityException | IllegalArgumentException unreadable) {
            throw new Unreadable("A sealed second-factor secret does not open under this server's key", unreadable);
        }
    }
}
