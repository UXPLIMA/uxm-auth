package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The second-factor block of config.conf.
 *
 * @param setup how the app is named, how its codes are made, how long a setup waits, and the backup codes
 * @param step which ways in lead to the code, and how many tries it gets
 */
public record SecondFactorSettings(SetUpTotp.Settings setup, SecondStep.Settings step) {
    public SecondFactorSettings {
        Objects.requireNonNull(setup, "setup");
        Objects.requireNonNull(step, "step");
    }

    /** Reads the block, naming it in any value it refuses. */
    static SecondFactorSettings read(HoconConfig config) {
        try {
            Totp totp = new Totp(
                    config.getInt("second-factor.digits", 6),
                    Duration.ofSeconds(config.getInt("second-factor.period-seconds", 30)),
                    config.getInt("second-factor.window-steps", 1));
            return new SecondFactorSettings(
                    new SetUpTotp.Settings(
                            config.getString("second-factor.issuer", "Minecraft"),
                            config.getString("second-factor.label", "{player}"),
                            totp,
                            Duration.ofMinutes(config.getInt("second-factor.setup-minutes", 10)),
                            new BackupCodes(
                                    config.getInt("second-factor.backup-codes", 8),
                                    config.getInt("second-factor.backup-code-length", 10))),
                    new SecondStep.Settings(
                            config.getBoolean("second-factor.premium-skips", true),
                            config.getBoolean("sessions.skip-second-factor", false),
                            config.getInt("passwords.max-attempts", 3)));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("second-factor: " + wrong.getMessage(), wrong);
        }
    }
}
