package com.uxplima.auth.testing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock a test turns forward by hand. */
public final class TurnedClock extends Clock {

    private volatile Instant now;

    public TurnedClock(Instant start) {
        this.now = start;
    }

    public void turn(Duration by) {
        now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
