package com.uxplima.auth.adapter.outbound;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;

/**
 * How the placeholders this plugin answers read.
 *
 * @param datePattern how a date and time read in a placeholder, in Java's pattern letters
 */
public record PlaceholderSettings(String datePattern) {
    public PlaceholderSettings {
        Objects.requireNonNull(datePattern, "datePattern");
        try {
            var unused = DateTimeFormatter.ofPattern(datePattern);
        } catch (IllegalArgumentException notAPattern) {
            throw new IllegalArgumentException(
                    "placeholders.date-format is no date pattern: " + datePattern, notAPattern);
        }
    }

    /** How a date and time read in a placeholder. */
    public DateTimeFormatter dates() {
        return DateTimeFormatter.ofPattern(datePattern);
    }

    static PlaceholderSettings read(HoconConfig config) {
        return new PlaceholderSettings(config.getString("placeholders.date-format", "yyyy-MM-dd HH:mm"));
    }
}
