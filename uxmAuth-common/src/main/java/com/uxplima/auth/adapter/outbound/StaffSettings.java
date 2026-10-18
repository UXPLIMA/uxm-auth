package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * What staff read and keep: how long the login history is kept, how much of an account a view shows, and how a
 * date reads.
 *
 * @param historyKeep how long a login stays in the history before it is pruned
 * @param info how many recent logins and other accounts a view shows
 * @param datePattern how a date and time read to staff, in Java's pattern letters
 */
public record StaffSettings(Duration historyKeep, AccountInfo.Settings info, String datePattern) {
    public StaffSettings {
        Objects.requireNonNull(historyKeep, "historyKeep");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(datePattern, "datePattern");
        try {
            var unused = DateTimeFormatter.ofPattern(datePattern);
        } catch (IllegalArgumentException notAPattern) {
            throw new IllegalArgumentException("staff.date-format is no date pattern: " + datePattern, notAPattern);
        }
    }

    /** How a date and time read to staff. */
    public DateTimeFormatter dates() {
        return DateTimeFormatter.ofPattern(datePattern);
    }

    /** Reads the blocks, naming the key in any value it refuses. */
    static StaffSettings read(HoconConfig config) {
        return new StaffSettings(
                Duration.ofDays(SettingsReader.positive(config, "history.keep-days", 90)),
                new AccountInfo.Settings(
                        SettingsReader.positive(config, "staff.recent-logins", 10),
                        SettingsReader.positive(config, "staff.alts-shown", 20)),
                config.getString("staff.date-format", "yyyy-MM-dd HH:mm"));
    }
}
