package com.uxplima.auth.application.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The time spent hashing, querying and asking Mojang: count, average and worst, over a minute and since start. */
final class TimingsTest {

    private long now = TimeUnit.HOURS.toNanos(5);
    private final Timings timings = new Timings(() -> now);

    private static long millis(long ms) {
        return TimeUnit.MILLISECONDS.toNanos(ms);
    }

    @Test
    @DisplayName("each kind keeps its own count, average and worst, over the minute and since start")
    void eachKindIsKept() {
        timings.record(Timings.Kind.HASHING, millis(40));
        timings.record(Timings.Kind.HASHING, millis(60));
        timings.record(Timings.Kind.DATABASE, millis(3));

        Timings.Report hashing = timings.report(Timings.Kind.HASHING);
        assertThat(hashing.lastMinute())
                .isEqualTo(new Timings.Summary(2, Duration.ofMillis(50), Duration.ofMillis(60)));
        assertThat(hashing.sinceStart()).isEqualTo(hashing.lastMinute());
        assertThat(timings.report(Timings.Kind.DATABASE).lastMinute())
                .isEqualTo(new Timings.Summary(1, Duration.ofMillis(3), Duration.ofMillis(3)));
        assertThat(timings.report(Timings.Kind.MOJANG).sinceStart()).isEqualTo(Timings.Summary.NONE);
    }

    @Test
    @DisplayName("the minute rolls: what is older than sixty seconds leaves it, and stays in since start")
    void theWindowRolls() {
        timings.record(Timings.Kind.MOJANG, millis(900));
        now += TimeUnit.SECONDS.toNanos(30);
        timings.record(Timings.Kind.MOJANG, millis(100));

        assertThat(timings.report(Timings.Kind.MOJANG).lastMinute())
                .isEqualTo(new Timings.Summary(2, Duration.ofMillis(500), Duration.ofMillis(900)));
        now += TimeUnit.SECONDS.toNanos(31);
        assertThat(timings.report(Timings.Kind.MOJANG).lastMinute())
                .isEqualTo(new Timings.Summary(1, Duration.ofMillis(100), Duration.ofMillis(100)));
        now += TimeUnit.SECONDS.toNanos(60);
        assertThat(timings.report(Timings.Kind.MOJANG).lastMinute()).isEqualTo(Timings.Summary.NONE);
        assertThat(timings.report(Timings.Kind.MOJANG).sinceStart())
                .isEqualTo(new Timings.Summary(2, Duration.ofMillis(500), Duration.ofMillis(900)));
    }

    @Test
    @DisplayName(
            "a second slot used again a minute later starts clean, and the minute an hour later holds only its own")
    void aReusedSlotStartsClean() {
        timings.record(Timings.Kind.DATABASE, millis(7));
        now += TimeUnit.SECONDS.toNanos(60);
        timings.record(Timings.Kind.DATABASE, millis(2));

        assertThat(timings.report(Timings.Kind.DATABASE).lastMinute())
                .isEqualTo(new Timings.Summary(1, Duration.ofMillis(2), Duration.ofMillis(2)));
        now += TimeUnit.HOURS.toNanos(1);
        timings.record(Timings.Kind.DATABASE, millis(5));
        assertThat(timings.report(Timings.Kind.DATABASE).lastMinute())
                .isEqualTo(new Timings.Summary(1, Duration.ofMillis(5), Duration.ofMillis(5)));
        assertThat(timings.report(Timings.Kind.DATABASE).sinceStart())
                .isEqualTo(new Timings.Summary(3, Duration.ofNanos(millis(14) / 3), Duration.ofMillis(7)));
    }

    @Test
    @DisplayName("timing a piece of work records the ticks it took, and every kind is reported in its order")
    void workIsTimed() {
        long start = timings.start();
        now += millis(12);
        timings.stop(Timings.Kind.HASHING, start);

        assertThat(timings.report(Timings.Kind.HASHING).sinceStart())
                .isEqualTo(new Timings.Summary(1, Duration.ofMillis(12), Duration.ofMillis(12)));
        assertThat(timings.reports()).extracting(Timings.Report::kind).containsExactly(Timings.Kind.values());
    }

    @Test
    @DisplayName("a sample fifty-nine seconds old is in the minute, and one sixty seconds old has left it")
    void theMinuteIsSixtySeconds() {
        timings.record(Timings.Kind.HASHING, millis(8));
        now += TimeUnit.SECONDS.toNanos(59);
        assertThat(timings.report(Timings.Kind.HASHING).lastMinute().count()).isEqualTo(1);
        now += TimeUnit.SECONDS.toNanos(1);
        assertThat(timings.report(Timings.Kind.HASHING).lastMinute()).isEqualTo(Timings.Summary.NONE);
    }
}
