package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each hasher against a hash some other program wrote.
 *
 * <p>The Argon2id and BCrypt vectors were made with argon2-cffi and the Python bcrypt module, the PBKDF2 one
 * with Python's hashlib, all on 2026-10-17. The AuthMe vector is AuthMe's own default format as recorded in
 * hashcat issue 1486. A hasher that only verifies its own output proves nothing about the hashes an imported
 * database holds.
 */
final class HasherVectorsTest {

    private static boolean verify(PasswordHasher hasher, String password, String hash) {
        try (Password typed = Password.of(password)) {
            return hasher.verify(typed, new PasswordHash(hash));
        }
    }

    @Test
    @DisplayName("an AuthMe SHA256 hash verifies its password and refuses a near miss")
    void authMeVectorVerifies() {
        String stored = "$SHA$c7dedf5a36c4a343$05ae3239eee683872ef1cc9096777bf4b1a72a179709efc17d8bf1603b082065";

        assertThat(verify(new AuthMeSha256Hasher(), "pantof", stored)).isTrue();
        assertThat(verify(new AuthMeSha256Hasher(), "pantoff", stored)).isFalse();
    }

    @Test
    @DisplayName("an Argon2id hash written by argon2-cffi verifies, with its own parameters")
    void argon2idVectorVerifies() {
        Argon2idHasher hasher = new Argon2idHasher(65536, 3, 1);

        assertThat(
                        verify(
                                hasher,
                                "password",
                                "$argon2id$v=19$m=65536,t=2,p=1$c29tZXNhbHQxMjM0NTY3OA$Hmk49RH516iPHGpKSdRGaFzi4/WOzzNeB5UJIKAgHbs"))
                .isTrue();
        assertThat(
                        verify(
                                hasher,
                                "Şifreğİ1",
                                "$argon2id$v=19$m=65536,t=3,p=1$dHV6c2FsdHNhbHRzYWx0MQ$wn/hsqppe7H3yqQj+NZQzLXq7f+XBt25REqSLQpDS70"))
                .isTrue();
    }

    @Test
    @DisplayName("BCrypt hashes under every prefix verify")
    void bcryptReadsEveryPrefix() {
        BcryptHasher hasher = new BcryptHasher(12);

        assertThat(verify(hasher, "password", "$2b$10$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK"))
                .isTrue();
        assertThat(verify(hasher, "password", "$2a$10$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK"))
                .isTrue();
        assertThat(verify(hasher, "Şifreğİ1", "$2y$10$abcdefghijklmnopqrstuurPnzXnWtHMiY8oTl6PjtlkbaZ.nozZW"))
                .isTrue();
        assertThat(verify(hasher, "passwore", "$2b$10$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK"))
                .isFalse();
    }

    @Test
    @DisplayName("a PBKDF2 hash written by Python's hashlib verifies")
    void pbkdf2VectorVerifies() {
        assertThat(verify(
                        new Pbkdf2Hasher(600000),
                        "password",
                        "pbkdf2-sha256$1000$c2FsdHNhbHRzYWx0c2FsdA==$8nX7hwFEzIB8aPajJTYK8weHQc5Ngz0pFVAKvSu4jQA="))
                .isTrue();
    }

    @Test
    @DisplayName("each hasher verifies its own hash and refuses another password")
    void eachHasherRoundTrips() {
        for (PasswordHasher hasher : fastHashers()) {
            try (Password right = Password.of("Correct-Horse-1");
                    Password wrong = Password.of("Correct-Horse-2")) {
                PasswordHash hash = hasher.hash(right);

                assertThat(hasher.verify(right, hash))
                        .as(hasher.algorithm().name())
                        .isTrue();
                assertThat(hasher.verify(wrong, hash))
                        .as(hasher.algorithm().name())
                        .isFalse();
            }
        }
    }

    @Test
    @DisplayName("two hashes of one password differ, because every one has its own salt")
    void twoHashesOfOnePasswordDiffer() {
        for (PasswordHasher hasher : fastHashers()) {
            try (Password password = Password.of("Correct-Horse-1")) {
                assertThat(hasher.hash(password)).as(hasher.algorithm().name()).isNotEqualTo(hasher.hash(password));
            }
        }
    }

    @Test
    @DisplayName("a Turkish password hashes and verifies under all four")
    void turkishPasswordRoundTrips() {
        for (PasswordHasher hasher : fastHashers()) {
            try (Password password = Password.of("Şifreğİ1")) {
                assertThat(hasher.verify(password, hasher.hash(password)))
                        .as(hasher.algorithm().name())
                        .isTrue();
            }
        }
    }

    /** Each algorithm at a cost a test can afford. The cost does not change what is proved. */
    private static List<PasswordHasher> fastHashers() {
        return List.of(
                new Argon2idHasher(1024, 1, 1), new BcryptHasher(4), new Pbkdf2Hasher(1000), new AuthMeSha256Hasher());
    }
}
