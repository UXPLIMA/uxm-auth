package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A second factor answers with the app's code for now, or once with a backup code. */
final class FactorCheckTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final Totp TOTP = new Totp(6, Duration.ofSeconds(30), 1);
    private static final TotpSecret SECRET = new TotpSecret(new byte[20]);
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    private final InMemoryFactors factors = new InMemoryFactors();
    private final Hashing hashing = new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4)));
    private final java.util.List<String> mailedTaken = new java.util.ArrayList<>();
    private final FactorCheck check =
            new FactorCheck(factors, hashing, () -> TOTP, () -> new BackupCodes(8, 10), (account, typed) -> {
                mailedTaken.add(typed);
                return "424242".equals(typed);
            });

    private String appCode() {
        return TOTP.code(SECRET.bytes(), TOTP.step(NOW));
    }

    private void backupCodes(String... codes) {
        factors.setBackupCodes(
                STEVE,
                List.of(codes).stream()
                        .map(code -> hashing.hash(Password.of(code)))
                        .toList());
    }

    @Test
    @DisplayName("the app's code for now passes once")
    void theAppsCodePassesOnce() {
        factors.setApp(STEVE, SECRET);

        assertThat(check.passes(STEVE, appCode(), NOW)).isTrue();
        assertThat(check.passes(STEVE, appCode(), NOW)).isFalse();
        assertThat(factors.app(STEVE))
                .hasValueSatisfying(app -> assertThat(app.lastStep()).isEqualTo(TOTP.step(NOW)));
    }

    @Test
    @DisplayName("a backup code passes once, typed any way, and is gone after; the others stay")
    void aBackupCodePassesOnce() {
        factors.setApp(STEVE, SECRET);
        backupCodes("ABCDEFGHJK", "MNPQRSTUVW");

        assertThat(check.passes(STEVE, "abcde-fghjk", NOW)).isTrue();
        assertThat(check.passes(STEVE, "ABCDEFGHJK", NOW)).isFalse();
        assertThat(factors.backupCodes(STEVE)).hasSize(1);
        assertThat(check.passes(STEVE, "MNPQR STUVW", NOW)).isTrue();
        assertThat(factors.backupCodes(STEVE)).isEmpty();
    }

    @Test
    @DisplayName("a wrong code, a code shaped like neither, and any code of an account with no factor fail")
    void wrongCodesFail() {
        factors.setApp(STEVE, SECRET);
        backupCodes("ABCDEFGHJK");

        assertThat(check.passes(STEVE, "000000", NOW)).isFalse();
        assertThat(check.passes(STEVE, "ZZZZZZZZZZ", NOW)).isFalse();
        assertThat(check.passes(STEVE, "", NOW)).isFalse();
        assertThat(check.passes(UUID.randomUUID(), appCode(), NOW)).isFalse();
        assertThat(factors.backupCodes(STEVE)).hasSize(1);
    }

    @Test
    @DisplayName("a backup code another server spent between the read and the spend does not pass")
    void aBackupCodeIsSpentOnce() {
        backupCodes("ABCDEFGHJK");
        InMemoryFactors racing = new InMemoryFactors() {
            @Override
            public boolean useBackupCode(UUID account, com.uxplima.auth.domain.hash.PasswordHash code) {
                return false;
            }
        };
        racing.setBackupCodes(STEVE, factors.backupCodes(STEVE));

        assertThat(new FactorCheck(
                                racing, hashing, () -> TOTP, () -> new BackupCodes(8, 10), FactorCheck.MailedCode.NONE)
                        .passes(STEVE, "ABCDEFGHJK", NOW))
                .isFalse();
    }

    @Test
    @DisplayName("a code the app does not take is tried as the mailed code; one the app takes is never")
    void aMailedCodePasses() {
        factors.setApp(STEVE, SECRET);

        assertThat(check.passes(STEVE, appCode(), NOW)).isTrue();
        assertThat(mailedTaken).isEmpty();
        assertThat(check.passes(STEVE, "424242", NOW)).isTrue();
        assertThat(check.passes(UUID.randomUUID(), "424242", NOW))
                .as("no app at all")
                .isTrue();
        assertThat(check.passes(STEVE, "000000", NOW)).isFalse();
    }
}
