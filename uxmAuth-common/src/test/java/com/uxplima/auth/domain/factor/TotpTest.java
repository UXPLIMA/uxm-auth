package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TotpTest {

    // The SHA-1 seed of RFC 6238, appendix B.
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Totp EIGHT_DIGITS = new Totp(8, Duration.ofSeconds(30), 1);
    private static final Totp APP = new Totp(6, Duration.ofSeconds(30), 1);

    @ParameterizedTest
    @CsvSource({
        "59, 94287082",
        "1111111109, 07081804",
        "1111111111, 14050471",
        "1234567890, 89005924",
        "2000000000, 69279037",
        "20000000000, 65353130"
    })
    void codesMatchTheRfcVectors(long epochSecond, String expected) {
        Instant at = Instant.ofEpochSecond(epochSecond);

        assertThat(EIGHT_DIGITS.code(RFC_SECRET, EIGHT_DIGITS.step(at))).isEqualTo(expected);
    }

    @Test
    void theCurrentCodeIsAcceptedAndReturnsItsStep() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = APP.step(now);

        assertThat(APP.verify(RFC_SECRET, APP.code(RFC_SECRET, step), now, Long.MIN_VALUE))
                .hasValue(step);
    }

    @Test
    void aCodeOneStepOffIsAcceptedInsideTheWindowOnly() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = APP.step(now);
        Totp strict = new Totp(6, Duration.ofSeconds(30), 0);

        assertThat(APP.verify(RFC_SECRET, APP.code(RFC_SECRET, step - 1), now, Long.MIN_VALUE))
                .hasValue(step - 1);
        assertThat(APP.verify(RFC_SECRET, APP.code(RFC_SECRET, step - 2), now, Long.MIN_VALUE))
                .isEmpty();
        assertThat(strict.verify(RFC_SECRET, APP.code(RFC_SECRET, step - 1), now, Long.MIN_VALUE))
                .isEmpty();
    }

    @Test
    void aCodeAlreadyUsedCannotBeReplayed() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        long step = APP.step(now);
        String code = APP.code(RFC_SECRET, step);

        assertThat(APP.verify(RFC_SECRET, code, now, step)).isEmpty();
    }

    @Test
    void spacesAreIgnoredAndOtherInputIsRefused() {
        Instant now = Instant.ofEpochSecond(1_234_567_890);
        String code = APP.code(RFC_SECRET, APP.step(now));

        assertThat(APP.verify(RFC_SECRET, code.substring(0, 3) + " " + code.substring(3), now, Long.MIN_VALUE))
                .isPresent();
        assertThat(APP.verify(RFC_SECRET, code.substring(1), now, Long.MIN_VALUE))
                .isEmpty();
        assertThat(APP.verify(RFC_SECRET, "abcdef", now, Long.MIN_VALUE)).isEmpty();
    }

    @Test
    void settingsOutsideWhatAppsSupportAreRefused() {
        assertThatThrownBy(() -> new Totp(5, Duration.ofSeconds(30), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Totp(6, Duration.ofSeconds(10), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Totp(6, Duration.ofSeconds(30), 5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSecretSurvivesBase32() {
        TotpSecret secret = new TotpSecret(RFC_SECRET);

        assertThat(secret.base32()).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpSecret.fromBase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"))
                .isEqualTo(secret);
        assertThat(secret.toString()).doesNotContain(secret.base32());
    }

    @Test
    void base32RefusesALetterOutsideItsAlphabet() {
        assertThatThrownBy(() -> Base32.decode("GEZ1")).isInstanceOf(IllegalArgumentException.class);
    }
}
