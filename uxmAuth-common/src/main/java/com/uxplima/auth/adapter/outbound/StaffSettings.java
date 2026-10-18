package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * What staff read and keep: how long the login history is kept.
 *
 * @param historyKeep how long a login stays in the history before it is pruned
 */
public record StaffSettings(Duration historyKeep) {
    public StaffSettings {
        Objects.requireNonNull(historyKeep, "historyKeep");
    }

    /** Reads the blocks, naming the key in any value it refuses. */
    static StaffSettings read(HoconConfig config) {
        return new StaffSettings(Duration.ofDays(SettingsReader.positive(config, "history.keep-days", 90)));
    }
}
