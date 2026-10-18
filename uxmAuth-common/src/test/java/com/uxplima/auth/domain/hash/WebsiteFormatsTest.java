package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The formats a website's login table holds, read and written the way the website reads and writes them. The PBKDF2
 * vectors were computed with Python's hashlib; the Argon2i one is the reference implementation's own test vector.
 */
final class WebsiteFormatsTest {

    private static final String PBKDF2_PASSWORD =
            "pbkdf2_sha256$10000$b25801311edf$093E38B16DFF13FCE5CD64D5D888EE6E0376A3E5"
                    + "72FE5DA6749515EA0F384413223A21C464B0BE899E64084D1FFEFD44F2AC768453C87F41B42CC6954C416900";
    private static final String PBKDF2_UNICODE = "pbkdf2_sha256$10000$fedcba9876543210$CB353186B3DA516D595DDE9240AA109B"
            + "8A912F934C812F72FA9E13BC7BE9CB1015E19D81FC2C92B91E1459E17FBBEB5BD8E2CC4831B9E53A3A099C23F924ADB1";
    private static final String ARGON2I_REFERENCE =
            "$argon2i$v=19$m=65536,t=2,p=1$c29tZXNhbHQ$wWKIMhR9lyDFvRz9YTZweHKfbftvj+qf+YFY4NeBbtA";

    @Test
    @DisplayName("PBKDF2 in hex reads the website's hashes and writes its own the same way")
    void pbkdf2InHex() {
        Pbkdf2HexHasher hasher = new Pbkdf2HexHasher(10_000);

        assertThat(hasher.verify(Password.of("password"), new PasswordHash(PBKDF2_PASSWORD)))
                .isTrue();
        assertThat(hasher.verify(Password.of("âË_3(íù*"), new PasswordHash(PBKDF2_UNICODE)))
                .isTrue();
        assertThat(hasher.verify(Password.of("Password"), new PasswordHash(PBKDF2_PASSWORD)))
                .isFalse();
        PasswordHash written = hasher.hash(Password.of("hunter22"));
        assertThat(written.encoded()).matches("pbkdf2_sha256\\$10000\\$[0-9a-f]{16}\\$[0-9A-F]{128}");
        assertThat(hasher.verify(Password.of("hunter22"), written)).isTrue();
        assertThat(hasher.isCurrent(written)).isTrue();
        assertThat(hasher.isCurrent(new PasswordHash(PBKDF2_PASSWORD))).isTrue();
        assertThat(new Pbkdf2HexHasher(20_000).isCurrent(written)).isFalse();
        assertThat(hasher.algorithm()).isEqualTo(HashAlgorithm.PBKDF2_HEX);
    }

    @Test
    @DisplayName("Argon2i reads the reference vector and writes the website's parameters")
    void argon2i() {
        Argon2iHasher hasher = new Argon2iHasher(65536, 2, 1);

        assertThat(hasher.verify(Password.of("password"), new PasswordHash(ARGON2I_REFERENCE)))
                .isTrue();
        assertThat(hasher.verify(Password.of("passwore"), new PasswordHash(ARGON2I_REFERENCE)))
                .isFalse();
        PasswordHash written = hasher.hash(Password.of("hunter22"));
        assertThat(written.encoded())
                .matches("\\$argon2i\\$v=19\\$m=65536,t=2,p=1\\$[A-Za-z0-9+/]{22}\\$[A-Za-z0-9+/]{43}");
        assertThat(hasher.verify(Password.of("hunter22"), written)).isTrue();
        assertThat(hasher.isCurrent(written)).isTrue();
        assertThat(hasher.algorithm()).isEqualTo(HashAlgorithm.ARGON2I);
        assertThat(hasher.verify(
                        Password.of("hunter22"), new Argon2idHasher(65536, 2, 1).hash(Password.of("hunter22"))))
                .as("an argon2id hash is not an argon2i one")
                .isFalse();
    }

    @Test
    @DisplayName("BCrypt for a website is written as $2a$ at its cost, and reads every bcrypt")
    void bcryptForAWebsite() {
        BcryptHasher hasher = new BcryptHasher(4, "2a");

        PasswordHash written = hasher.hash(Password.of("hunter22"));
        assertThat(written.encoded()).startsWith("$2a$04$");
        assertThat(hasher.verify(Password.of("hunter22"), written)).isTrue();
        assertThat(hasher.verify(Password.of("hunter22"), new BcryptHasher(4).hash(Password.of("hunter22"))))
                .isTrue();
        assertThat(new BcryptHasher(4).hash(Password.of("x1")).encoded()).startsWith("$2b$");
    }

    @Test
    @DisplayName("each website format is recognised as its own, and argon2i never as argon2id")
    void formatsAreRecognised() {
        assertThat(HashFormats.recognise(new PasswordHash(PBKDF2_PASSWORD))).contains(HashAlgorithm.PBKDF2_HEX);
        assertThat(HashFormats.recognise(new PasswordHash(ARGON2I_REFERENCE))).contains(HashAlgorithm.ARGON2I);
        assertThat(HashFormats.recognise(new PasswordHash("$argon2id$v=19$m=8,t=1,p=1$c2FsdHNhbHQ$aGFzaA")))
                .contains(HashAlgorithm.ARGON2ID);
        assertThat(HashFormats.recognise(new PasswordHash("pbkdf2-sha256$1$c2FsdA==$aGFzaA==")))
                .contains(HashAlgorithm.PBKDF2);
    }

    @Test
    @DisplayName("a hashing whose current hasher is a website's reads its hashes and moves an old one to it")
    void aWebsiteHashingMovesOldHashes() {
        Hashing website = new Hashing(new Pbkdf2HexHasher(10_000), java.util.List.of(new Argon2idHasher(1024, 1, 1)));

        assertThat(website.verify(Password.of("password"), new PasswordHash(PBKDF2_PASSWORD)))
                .isInstanceOf(Verification.Right.class);
        assertThat(website.verify(
                        Password.of("hunter22"), new Argon2idHasher(1024, 1, 1).hash(Password.of("hunter22"))))
                .isInstanceOfSatisfying(
                        Verification.RightButRehash.class,
                        rehash -> assertThat(rehash.fresh().encoded()).startsWith("pbkdf2_sha256$10000$"));
    }
}
