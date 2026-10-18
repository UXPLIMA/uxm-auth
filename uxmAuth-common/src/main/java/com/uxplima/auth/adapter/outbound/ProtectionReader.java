package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.uxplima.auth.adapter.outbound.geo.GeoSettings;
import com.uxplima.auth.application.protection.AccountCap;
import com.uxplima.auth.application.protection.ConnectionWindow;
import com.uxplima.auth.application.protection.CountryRule;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.uxmlib.config.HoconConfig;

/** Reads the protection block of config.conf into its record. */
final class ProtectionReader {

    private static final List<Integer> SHIPPED_STEPS = List.of(5, 30, 60, 1440);

    private ProtectionReader() {}

    static ProtectionSettings protection(HoconConfig config) {
        return new ProtectionSettings(
                list(config, "shared-address-hosts"),
                list(config, "shared-address-sources"),
                sessions(config),
                bans(config),
                connections(config),
                accounts(config),
                countries(config));
    }

    private static GeoSettings countries(HoconConfig config) {
        String mode =
                config.getString("protection.countries.mode", "off").strip().toUpperCase(Locale.ROOT);
        String unknown = config.getString("protection.countries.unknown", "allow")
                .strip()
                .toLowerCase(Locale.ROOT);
        if (!unknown.equals("allow") && !unknown.equals("deny")) {
            throw new IllegalArgumentException("protection.countries.unknown is " + unknown + "; write allow or deny");
        }
        try {
            CountryRule.Settings rule = new CountryRule.Settings(
                    CountryRule.Mode.valueOf(mode),
                    Set.copyOf(list(config, "countries.list")),
                    new AddressRanges(list(config, "countries.allow-addresses")),
                    unknown.equals("allow"));
            GeoSettings.Licence licence = new GeoSettings.Licence(
                    config.getString("protection.countries.account-id", ""),
                    config.getString("protection.countries.licence-key", ""));
            return new GeoSettings(
                    rule, licence, Duration.ofDays(config.getInt("protection.countries.refresh-days", 7)));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("protection.countries: " + wrong.getMessage(), wrong);
        }
    }

    private static AccountCap.Settings accounts(HoconConfig config) {
        int perAddress = config.getInt("protection.accounts.per-address", 3);
        int hours = config.getInt("protection.accounts.window-hours", 24);
        try {
            return new AccountCap.Settings(
                    perAddress, Duration.ofHours(hours), new AddressRanges(list(config, "accounts.exempt-addresses")));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("protection.accounts: " + wrong.getMessage(), wrong);
        }
    }

    private static ConnectionWindow.Settings connections(HoconConfig config) {
        int perAddress = config.getInt("protection.connections.per-address", 5);
        int seconds = config.getInt("protection.connections.window-seconds", 60);
        try {
            return new ConnectionWindow.Settings(
                    perAddress,
                    Duration.ofSeconds(seconds),
                    new AddressRanges(list(config, "connections.exempt-addresses")));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("protection.connections: " + wrong.getMessage(), wrong);
        }
    }

    private static BanSteps bans(HoconConfig config) {
        List<Duration> steps = (config.nodeAt("protection", "bans", "steps-minutes")
                                .virtual()
                        ? SHIPPED_STEPS
                        : config.getList("protection.bans.steps-minutes", Integer.class))
                .stream().map(minutes -> Duration.ofMinutes(minutes)).toList();
        int failures = config.getInt("protection.bans.failures", 3);
        int hours = config.getInt("protection.bans.forgive-after-hours", 24);
        try {
            return new BanSteps(steps, failures, Duration.ofHours(hours));
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("protection.bans: " + wrong.getMessage(), wrong);
        }
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
