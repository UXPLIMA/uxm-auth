package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import com.uxplima.auth.application.protection.Remember;
import com.uxplima.uxmlib.config.HoconConfig;

/** Reads the protection block of config.conf into its record. */
final class ProtectionReader {

    private ProtectionReader() {}

    static ProtectionSettings protection(HoconConfig config) {
        return new ProtectionSettings(list(config, "shared-address-hosts"), sessions(config));
    }

    private static Remember.Settings sessions(HoconConfig config) {
        int minutes = config.getInt("sessions.minutes", 60);
        if (minutes < 1) {
            throw new IllegalArgumentException("sessions.minutes must be at least 1, got: " + minutes);
        }
        String mode = config.getString("sessions.mode", "chosen").trim().toUpperCase(Locale.ROOT);
        try {
            return new Remember.Settings(Remember.Mode.valueOf(mode), Duration.ofMinutes(minutes));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(
                    "sessions.mode is " + mode.toLowerCase(Locale.ROOT) + "; write off, everyone or chosen", unknown);
        }
    }

    private static List<String> list(HoconConfig config, String name) {
        String path = "protection." + name;
        if (config.nodeAt((Object[]) path.split("\\.")).virtual()) {
            return List.of();
        }
        return List.copyOf(config.getList(path, String.class));
    }
}
