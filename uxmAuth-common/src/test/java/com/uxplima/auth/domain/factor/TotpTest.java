package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Time-based codes, RFC 6238 with HMAC-SHA1, as every authenticator app makes them. */
final class TotpTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static Instant at(long seconds) {
        return Instant.ofEpochSecond(seconds);
    }

    @Test
    @DisplayName("the RFC 6238 SHA-1 vectors, eight digits on thirty-second steps")
    void theRfcVectors() {
        Totp totp = new Totp(8, Duration.ofSeconds(30), 1);
        long[][] vectors = {
            {59L, 94287082L},
            {1111111109L, 7081804L},
            {1111111111L, 14050471L},
            {1234567890L, 89005924L},
            {2000000000L, 69279037L},
            {20000000000L, 65353130L}
        };
        for (long[] vector : vectors) {
            assertThat(totp.code(RFC_SECRET, totp.step(at(vector[0]))))
                    .as("at %d", vector[0])
                    .isEqualTo(String.format("%08d", vector[1]));
        }
    }

    @Test
    @DisplayName("six digits by default length, and a code one step either side of now is taken, two is not")
    void theWindow() {
        Totp totp = new Totp(6, Duration.ofSeconds(30), 1);
        Instant now = at(1_700_000_000L);
        long step = totp.step(now);

        assertThat(totp.code(RFC_SECRET, step)).hasSize(6).containsOnlyDigits();
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step - 1), now, -1))
                .hasValue(step - 1);
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step + 1), now, -1))
                .hasValue(step + 1);
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step - 2), now, -1))
                .isEmpty();
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step + 2), now, -1))
                .isEmpty();
        assertThat(new Totp(6, Duration.ofSeconds(30), 2).verify(RFC_SECRET, totp.code(RFC_SECRET, step - 2), now, -1))
                .hasValue(step - 2);
    }

    @Test
    @DisplayName("a code is used once: a step at or before the last one used is refused")
    void aCodeIsUsedOnce() {
        Totp totp = new Totp(6, Duration.ofSeconds(30), 1);
        Instant now = at(1_700_000_000L);
        long step = totp.step(now);
        String code = totp.code(RFC_SECRET, step);

        OptionalLong first = totp.verify(RFC_SECRET, code, now, -1);
        assertThat(first).hasValue(step);
        assertThat(totp.verify(RFC_SECRET, code, now, first.getAsLong())).isEmpty();
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step - 1), now, step))
                .isEmpty();
        assertThat(totp.verify(RFC_SECRET, totp.code(RFC_SECRET, step + 1), now, step))
                .hasValue(step + 1);
    }

    @Test
    @DisplayName("a typed code is read with spaces, and anything not a code of that length is refused")
    void typedCodes() {
        Totp totp = new Totp(6, Duration.ofSeconds(30), 1);
        Instant now = at(1_700_000_000L);
        String code = totp.code(RFC_SECRET, totp.step(now));

        assertThat(totp.verify(RFC_SECRET, code.substring(0, 3) + " " + code.substring(3), now, -1))
                .isPresent();
        assertThat(totp.verify(RFC_SECRET, "12345", now, -1)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "abcdef", now, -1)).isEmpty();
        assertThat(totp.verify(RFC_SECRET, "", now, -1)).isEmpty();
    }

    @Test
    @DisplayName("a new secret is twenty random bytes, and the numbers an app accepts are refused otherwise")
    void secretsAndSettings() {
        assertThat(Totp.newSecret(new SplittableRandom(1))).hasSize(20);
        assertThat(Totp.newSecret(new SplittableRandom(1))).isNotEqualTo(Totp.newSecret(new SplittableRandom(2)));
        assertThatThrownBy(() -> new Totp(5, Duration.ofSeconds(30), 1)).hasMessageContaining("digits");
        assertThatThrownBy(() -> new Totp(9, Duration.ofSeconds(30), 1)).hasMessageContaining("digits");
        assertThatThrownBy(() -> new Totp(6, Duration.ofSeconds(10), 1)).hasMessageContaining("period");
        assertThatThrownBy(() -> new Totp(6, Duration.ofSeconds(30), -1)).hasMessageContaining("window");
        assertThatThrownBy(() -> new Totp(6, Duration.ofSeconds(30), 5)).hasMessageContaining("window");
    }
}
