package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The shared secret behind an authenticator app: its bytes are its own, and it never prints itself. */
final class TotpSecretTest {

    private static final byte[] RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    @DisplayName("a secret reads as the Base32 an app is given, and back")
    void readsAsBase32() {
        TotpSecret secret = new TotpSecret(RFC);

        assertThat(secret.base32()).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(TotpSecret.fromBase32(secret.base32())).isEqualTo(secret);
    }

    @Test
    @DisplayName("a secret keeps its own copy, equals by content, and hides itself in a log")
    void keepsItsOwnCopy() {
        byte[] given = RFC.clone();
        TotpSecret secret = new TotpSecret(given);
        given[0] = 0;
        secret.bytes()[1] = 0;

        assertThat(secret.bytes()).isEqualTo(RFC);
        assertThat(secret).isEqualTo(new TotpSecret(RFC)).hasSameHashCodeAs(new TotpSecret(RFC));
        assertThat(secret).isNotEqualTo(new TotpSecret(new byte[20]));
        assertThat(secret.toString()).doesNotContain(secret.base32()).contains("hidden");
    }

    @Test
    @DisplayName("a secret shorter than 80 bits is refused, as RFC 4226 asks at least that")
    void aShortSecretIsRefused() {
        assertThatThrownBy(() -> new TotpSecret(new byte[9])).hasMessageContaining("10");
        assertThat(new TotpSecret(new byte[10]).bytes()).hasSize(10);
    }
}
