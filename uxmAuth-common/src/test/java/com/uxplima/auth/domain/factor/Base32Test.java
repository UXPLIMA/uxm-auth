package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The alphabet an authenticator app reads a secret in: RFC 4648 Base32, without padding. */
final class Base32Test {

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    @DisplayName("the RFC 4648 vectors encode without padding and decode back")
    void theRfcVectors() {
        String[][] vectors = {
            {"", ""},
            {"f", "MY"},
            {"fo", "MZXQ"},
            {"foo", "MZXW6"},
            {"foob", "MZXW6YQ"},
            {"fooba", "MZXW6YTB"},
            {"foobar", "MZXW6YTBOI"}
        };
        for (String[] vector : vectors) {
            assertThat(Base32.encode(ascii(vector[0]))).as(vector[0]).isEqualTo(vector[1]);
            assertThat(Base32.decode(vector[1])).as(vector[1]).isEqualTo(ascii(vector[0]));
        }
    }

    @Test
    @DisplayName("a secret typed by hand is read in either case, with spaces and padding left out")
    void typedByHand() {
        assertThat(Base32.decode("mzxw 6ytb oi======")).isEqualTo(ascii("foobar"));
    }

    @Test
    @DisplayName("a character outside the alphabet is refused")
    void outsideTheAlphabetIsRefused() {
        assertThatThrownBy(() -> Base32.decode("MZXW1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base32.decode("MZXW8")).isInstanceOf(IllegalArgumentException.class);
    }
}
