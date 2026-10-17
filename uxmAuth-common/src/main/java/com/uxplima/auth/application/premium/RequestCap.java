package com.uxplima.auth.application.premium;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * How many requests a source is sent in ten minutes. Mojang refuses past 600, and a refusal there is a name
 * nobody can be sure of, so the cap stops a little before.
 */
public final class RequestCap {

    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final IntSupplier cap;
    private final Clock clock;
    private final Deque<Instant> sent = new ArrayDeque<>();

    public RequestCap(IntSupplier cap, Clock clock) {
        this.cap = Objects.requireNonNull(cap, "cap");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Counts one request and says yes, or says no while the last ten minutes are full. */
    public synchronized boolean take() {
        Instant now = clock.instant();
        Instant oldest = now.minus(WINDOW);
        while (!sent.isEmpty() && !sent.peekFirst().isAfter(oldest)) {
            sent.pollFirst();
        }
        if (sent.size() >= cap.getAsInt()) {
            return false;
        }
        sent.addLast(now);
        return true;
    }
}
