package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.auth.application.factor.SetUpTotp;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * The second-factor block of config.conf.
 *
 * @param setup how the app is named, how its codes are made, and how long a setup waits
 */
public record SecondFactorSettings(SetUpTotp.Settings setup) {
    public SecondFactorSettings {
        Objects.requireNonNull(setup, "setup");
    }

    /** Reads the block, naming it in any value it refuses. */
    static SecondFactorSettings read(HoconConfig config) {
        try {
            Totp totp = new Totp(
                    config.getInt("second-factor.digits", 6),
                    Duration.ofSeconds(config.getInt("second-factor.period-seconds", 30)),
                    config.getInt("second-factor.window-steps", 1));
            return new SecondFactorSettings(new SetUpTotp.Settings(
                    config.getString("second-factor.issuer", "Minecraft"),
                    config.getString("second-factor.label", "{player}"),
                    totp,
                    Duration.ofMinutes(config.getInt("second-factor.setup-minutes", 10))));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("second-factor: " + wrong.getMessage(), wrong);
        }
    }
}
