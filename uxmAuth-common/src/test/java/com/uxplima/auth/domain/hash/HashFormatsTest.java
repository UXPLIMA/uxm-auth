package com.uxplima.auth.domain.hash;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class HashFormatsTest {

    @Test
    @DisplayName("every stored format is recognised by its prefix")
    void recognisesEachFormat() {
        assertThat(HashFormats.recognise(new PasswordHash("$argon2id$v=19$m=1,t=1,p=1$a$b")))
                .contains(HashAlgorithm.ARGON2ID);
        assertThat(HashFormats.recognise(new PasswordHash("$2a$10$x"))).contains(HashAlgorithm.BCRYPT);
        assertThat(HashFormats.recognise(new PasswordHash("$2b$10$x"))).contains(HashAlgorithm.BCRYPT);
        assertThat(HashFormats.recognise(new PasswordHash("$2y$10$x"))).contains(HashAlgorithm.BCRYPT);
        assertThat(HashFormats.recognise(new PasswordHash("pbkdf2-sha256$1000$a$b")))
                .contains(HashAlgorithm.PBKDF2);
        assertThat(HashFormats.recognise(new PasswordHash("$SHA$salt$hash"))).contains(HashAlgorithm.AUTHME_SHA256);
    }

    @Test
    @DisplayName("a plain text value is not a hash of any kind")
    void plainTextIsNothing() {
        assertThat(HashFormats.recognise(new PasswordHash("plaintext"))).isEmpty();
    }
}
