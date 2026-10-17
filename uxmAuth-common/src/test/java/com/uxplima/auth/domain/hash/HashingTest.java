package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HashingTest {

    private static final String AUTHME =
            "$SHA$c7dedf5a36c4a343$05ae3239eee683872ef1cc9096777bf4b1a72a179709efc17d8bf1603b082065";

    private static Hashing argon2Current() {
        Argon2idHasher current = new Argon2idHasher(1024, 2, 1);
        return new Hashing(
                current, List.of(current, new BcryptHasher(4), new Pbkdf2Hasher(1000), new AuthMeSha256Hasher()));
    }

    @Test
    @DisplayName("the right password on a legacy hash is right, and comes with a hash in the current algorithm")
    void legacyRightPasswordIsRehashed() {
        try (Password password = Password.of("pantof")) {
            Verification verification = argon2Current().verify(password, new PasswordHash(AUTHME));

            assertThat(verification).isInstanceOf(Verification.RightButRehash.class);
            PasswordHash fresh = ((Verification.RightButRehash) verification).fresh();
            assertThat(HashFormats.recognise(fresh)).contains(HashAlgorithm.ARGON2ID);
        }
    }

    @Test
    @DisplayName("a hash in the current algorithm and parameters needs nothing")
    void currentAlgorithmIsNotRehashed() {
        Hashing hashing = argon2Current();
        try (Password password = Password.of("Correct-Horse-1")) {
            PasswordHash hash = hashing.hash(password);

            assertThat(hashing.verify(password, hash)).isEqualTo(new Verification.Right());
        }
    }

    @Test
    @DisplayName("a hash in the current algorithm with older parameters is hashed again")
    void olderParametersAreRehashed() {
        Hashing hashing = argon2Current();
        try (Password password = Password.of("Correct-Horse-1")) {
            PasswordHash older = new Argon2idHasher(1024, 1, 1).hash(password);

            assertThat(hashing.verify(password, older)).isInstanceOf(Verification.RightButRehash.class);
        }
    }

    @Test
    @DisplayName("a wrong password is wrong, and an unknown format is wrong rather than an error")
    void wrongIsWrong() {
        try (Password password = Password.of("pantoff")) {
            assertThat(argon2Current().verify(password, new PasswordHash(AUTHME)))
                    .isEqualTo(new Verification.Wrong());
            assertThat(argon2Current().verify(password, new PasswordHash("plaintext")))
                    .isEqualTo(new Verification.Wrong());
        }
    }
}
