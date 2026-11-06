package com.uxplima.auth.domain.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * How often codes of one kind went to one account: when the latest went, when its day began, and how many went
 * that day. It is kept apart from the code itself, so a code that is spent, used up or past its time does not take
 * its cooldown with it, and asking again never buys a fresh code with fresh tries sooner than the rules allow.
 *
 * @param last when the latest code went, nothing before any
 * @param dayStart when the day the count runs in began, nothing before any
 * @param sent how many codes went since the day began
 */
public record SendTally(@Nullable Instant last, @Nullable Instant dayStart, int sent) {

    /** How long the count of a day runs before it starts again. */
    public static final Duration DAY = Duration.ofDays(1);

    public SendTally {
        if (sent < 0) {
            throw new IllegalArgumentException("A count of codes counts up from nothing, got " + sent);
        }
    }

    /** No code ever went. */
    public static SendTally none() {
        return new SendTally(null, null, 0);
    }

    /**
     * How long before another code may go at this moment: until the cooldown since the latest one ran out, or, once
     * the day's cap is reached, until the day ends. Nothing to wait is zero.
     */
    public Duration waitAt(Instant now, Duration cooldown, int perDay) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(cooldown, "cooldown");
        Duration wait = Duration.ZERO;
        if (last != null && last.plus(cooldown).isAfter(now)) {
            wait = Duration.between(now, last.plus(cooldown));
        }
        if (dayStart != null && sent >= perDay && dayStart.plus(DAY).isAfter(now)) {
            Duration day = Duration.between(now, dayStart.plus(DAY));
            wait = day.compareTo(wait) > 0 ? day : wait;
        }
        return wait;
    }

    /** One more code went at this moment; a day that ended starts again with it. */
    public SendTally sentAt(Instant now) {
        Objects.requireNonNull(now, "now");
        if (dayStart == null || !now.isBefore(dayStart.plus(DAY))) {
            return new SendTally(now, now, 1);
        }
        return new SendTally(now, dayStart, sent + 1);
    }
}
