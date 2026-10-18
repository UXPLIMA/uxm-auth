package com.uxplima.auth.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A time left, in the units of the reader's language: its two largest, so a day reads as days and hours and a
 * few minutes as minutes and seconds. A part of a second counts as a whole one, so a wait never reads as over.
 */
public final class TimeWords {

    private static final Keys[] UNITS = {Keys.TIME_DAYS, Keys.TIME_HOURS, Keys.TIME_MINUTES, Keys.TIME_SECONDS};
    private static final long MILLIS_PER_SECOND = 1000;

    private final Messages messages;

    public TimeWords(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /** The time left, as this reader reads it. */
    public Component left(Audience reader, Duration left) {
        Objects.requireNonNull(reader, "reader");
        long seconds =
                Math.ceilDiv(Math.max(0, Objects.requireNonNull(left, "left").toMillis()), MILLIS_PER_SECOND);
        long[] amounts = {seconds / 86_400, seconds / 3_600 % 24, seconds / 60 % 60, seconds % 60};
        int first = 0;
        while (first < UNITS.length - 1 && amounts[first] == 0) {
            first++;
        }
        List<Component> parts = new ArrayList<>();
        parts.add(unit(reader, first, amounts[first]));
        if (first + 1 < UNITS.length && amounts[first + 1] != 0) {
            parts.add(unit(reader, first + 1, amounts[first + 1]));
        }
        return Component.join(JoinConfiguration.separator(messages.render(reader, Keys.TIME_SEPARATOR)), parts);
    }

    private Component unit(Audience reader, int index, long amount) {
        return messages.render(reader, UNITS[index], Text.placeholder("n", String.valueOf(amount)));
    }
}
