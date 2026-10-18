package com.uxplima.auth.application.perf;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.stream.Stream;

/**
 * The time spent hashing, querying the database and asking Mojang: how often, how long on average, and the worst,
 * over the last minute and since start. Recording is a few atomic adds into a slot per second, so it allocates
 * nothing and blocks nobody; a sample that races a slot being reused may be lost, which a live view can bear.
 */
public final class Timings {

    /** What is timed. */
    public enum Kind {
        HASHING("hashing"),
        DATABASE("database"),
        MOJANG("mojang");

        private final String key;

        Kind(String key) {
            this.key = key;
        }

        /** The word staff read for it. */
        public String key() {
            return key;
        }
    }

    /** How often, how long on average, and the worst. */
    public record Summary(long count, Duration average, Duration worst) {
        /** Nothing timed. */
        public static final Summary NONE = new Summary(0, Duration.ZERO, Duration.ZERO);

        public Summary {
            Objects.requireNonNull(average, "average");
            Objects.requireNonNull(worst, "worst");
        }

        static Summary of(long count, long sum, long worst) {
            return count == 0 ? NONE : new Summary(count, Duration.ofNanos(sum / count), Duration.ofNanos(worst));
        }
    }

    /** One kind, over the last minute and since start. */
    public record Report(Kind kind, Summary lastMinute, Summary sinceStart) {
        public Report {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(lastMinute, "lastMinute");
            Objects.requireNonNull(sinceStart, "sinceStart");
        }
    }

    private static final int SECONDS = 60;
    private static final long SECOND = TimeUnit.SECONDS.toNanos(1);

    /** The samples of one second, or of the whole run. */
    private static final class Tally {
        final AtomicLong second = new AtomicLong(Long.MIN_VALUE);
        final AtomicLong count = new AtomicLong();
        final AtomicLong sum = new AtomicLong();
        final AtomicLong worst = new AtomicLong();

        void add(long nanos) {
            count.incrementAndGet();
            sum.addAndGet(nanos);
            worst.accumulateAndGet(nanos, Math::max);
        }
    }

    private final LongSupplier ticks;
    /** What one kind keeps: a slot per second of the minute, and the run. */
    private static final class Kept {
        final Tally[] minute = new Tally[SECONDS];
        final Tally total = new Tally();

        Kept() {
            for (int second = 0; second < SECONDS; second++) {
                minute[second] = new Tally();
            }
        }
    }

    private final Map<Kind, Kept> kept = new EnumMap<>(Kind.class);

    /** @param ticks a monotonic clock in nanoseconds, which is {@link System#nanoTime} */
    public Timings(LongSupplier ticks) {
        this.ticks = Objects.requireNonNull(ticks, "ticks");
        for (Kind kind : Kind.values()) {
            kept.put(kind, new Kept());
        }
    }

    /** The tick a piece of work starts at, for {@link #stop}. */
    public long start() {
        return ticks.getAsLong();
    }

    /** The work of this kind that began at this tick is done. */
    public void stop(Kind kind, long start) {
        record(kind, ticks.getAsLong() - start);
    }

    public void record(Kind kind, long nanos) {
        long second = Math.floorDiv(ticks.getAsLong(), SECOND);
        Kept of = kept(kind);
        Tally slot = of.minute[Math.floorMod(second, SECONDS)];
        long stamp = slot.second.get();
        if (stamp != second && slot.second.compareAndSet(stamp, second)) {
            slot.count.set(0);
            slot.sum.set(0);
            slot.worst.set(0);
        }
        slot.add(Math.max(0, nanos));
        of.total.add(Math.max(0, nanos));
    }

    public Report report(Kind kind) {
        Kept of = kept(kind);
        long now = Math.floorDiv(ticks.getAsLong(), SECOND);
        long count = 0;
        long sum = 0;
        long worst = 0;
        for (Tally slot : of.minute) {
            long age = now - slot.second.get();
            if (age >= 0 && age < SECONDS) {
                count += slot.count.get();
                sum += slot.sum.get();
                worst = Math.max(worst, slot.worst.get());
            }
        }
        Tally total = of.total;
        return new Report(
                kind, Summary.of(count, sum, worst), Summary.of(total.count.get(), total.sum.get(), total.worst.get()));
    }

    private Kept kept(Kind kind) {
        return Objects.requireNonNull(kept.get(Objects.requireNonNull(kind, "kind")), "every kind is kept");
    }

    /** Every kind, in its order. */
    public List<Report> reports() {
        return Stream.of(Kind.values()).map(this::report).toList();
    }
}
