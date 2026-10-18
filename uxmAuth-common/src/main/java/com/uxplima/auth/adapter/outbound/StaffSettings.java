package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * What staff read and keep: how long the login history is kept, how much of an account a view shows, and how a
 * date reads.
 *
 * @param historyKeep how long a login stays in the history before it is pruned
 * @param info how many recent logins and other accounts a view shows
 * @param datePattern how a date and time read to staff, in Java's pattern letters
 * @param inGame the destructive staff commands the operator opened to staff in game
 */
public record StaffSettings(
        Duration historyKeep, AccountInfo.Settings info, String datePattern, Set<StaffAction> inGame) {
    public StaffSettings {
        Objects.requireNonNull(historyKeep, "historyKeep");
        Objects.requireNonNull(info, "info");
        Objects.requireNonNull(datePattern, "datePattern");
        inGame = Set.copyOf(inGame);
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

    /** Whether staff in game may run this: anything but a destructive command the operator did not open. */
    public boolean allowsInGame(StaffAction action) {
        return !Objects.requireNonNull(action, "action").destructive() || inGame.contains(action);
    }

    /** Reads the blocks, naming the key in any value it refuses. */
    static StaffSettings read(HoconConfig config) {
        return new StaffSettings(
                Duration.ofDays(SettingsReader.positive(config, "history.keep-days", 90)),
                new AccountInfo.Settings(
                        SettingsReader.positive(config, "staff.recent-logins", 10),
                        SettingsReader.positive(config, "staff.alts-shown", 20)),
                config.getString("staff.date-format", "yyyy-MM-dd HH:mm"),
                inGame(config));
    }

    private static Set<StaffAction> inGame(HoconConfig config) {
        Set<StaffAction> opened = EnumSet.noneOf(StaffAction.class);
        for (String key : config.getList("staff.in-game", String.class)) {
            opened.add(StaffAction.named(key)
                    .orElseThrow(() -> new IllegalArgumentException("staff.in-game names no staff command: " + key)));
        }
        return opened;
    }
}
