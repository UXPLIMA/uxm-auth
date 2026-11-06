package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;

import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import org.junit.jupiter.api.Test;

class FactorCheckTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Totp EIGHT_DIGITS = new Totp(8, Duration.ofSeconds(30), 0);
    private static final BackupCodes EIGHT_CHARACTERS = new BackupCodes(4, 8);

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final MemoryFactors factors = new MemoryFactors();
    private final UUID account = UUID.randomUUID();
    private final FactorCheck check =
            new FactorCheck(factors, hashing, () -> EIGHT_DIGITS, () -> EIGHT_CHARACTERS, FactorCheck.MailedCode.NONE);

    @Test
    void anAppCodeShapedLikeABackupCodeIsStillAskedOfTheApp() {
        factors.setApp(account, new TotpSecret(SECRET));
        Instant now = aMomentWhoseCodeLooksLikeABackupCode();
        String code = EIGHT_DIGITS.code(SECRET, EIGHT_DIGITS.step(now));
        assertThat(EIGHT_CHARACTERS.read(code)).isPresent();

        assertThat(check.passes(account, code, now)).isTrue();
    }

    @Test
    void aBackupCodeStillPassesOnceBesideTheApp() {
        factors.setApp(account, new TotpSecret(SECRET));
        List<String> codes = check.newBackupCodes(account, new SplittableRandom(7));
        Instant now = Instant.ofEpochSecond(1_234_567_890);

        assertThat(check.passes(account, codes.getFirst(), now)).isTrue();
        assertThat(check.passes(account, codes.getFirst(), now)).isFalse();
        assertThat(check.appOrBackup(account, codes.get(1), now)).isTrue();
    }

    /** A moment at which the eight digits of the app are all 2 to 9, which a backup code may be made of. */
    private static Instant aMomentWhoseCodeLooksLikeABackupCode() {
        for (long step = 1; ; step++) {
            String code = EIGHT_DIGITS.code(SECRET, step);
            if (code.chars().allMatch(c -> c >= '2' && c <= '9')) {
                return Instant.ofEpochSecond(step * 30);
            }
        }
    }

    /** The factor table in memory. */
    private static final class MemoryFactors implements Factors {

        private final Map<UUID, App> apps = new HashMap<>();
        private final Map<UUID, List<PasswordHash>> backups = new HashMap<>();

        @Override
        public Optional<App> app(UUID account) {
            return Optional.ofNullable(apps.get(account));
        }

        @Override
        public void setApp(UUID account, TotpSecret secret) {
            apps.put(account, new App(secret, -1));
        }

        @Override
        public void removeApp(UUID account) {
            apps.remove(account);
        }

        @Override
        public boolean useStep(UUID account, long step) {
            App app = apps.get(account);
            if (app == null || step <= app.lastStep()) {
                return false;
            }
            apps.put(account, new App(app.secret(), step));
            return true;
        }

        @Override
        public List<PasswordHash> backupCodes(UUID account) {
            return List.copyOf(backups.getOrDefault(account, List.of()));
        }

        @Override
        public void setBackupCodes(UUID account, List<PasswordHash> codes) {
            backups.put(account, new ArrayList<>(codes));
        }

        @Override
        public boolean useBackupCode(UUID account, PasswordHash code) {
            List<PasswordHash> kept = backups.get(account);
            return kept != null && kept.remove(code);
        }
    }
}
