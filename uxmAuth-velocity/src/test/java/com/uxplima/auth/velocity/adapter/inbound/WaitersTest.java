package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WaitersTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final InetSocketAddress BOB = new InetSocketAddress("203.0.113.7", 50000);
    private static final InetSocketAddress MALLORY = new InetSocketAddress("198.51.100.9", 50001);
    private static final UUID ID = UUID.fromString("6f8e2b1a-3c4d-4e5f-8a9b-0c1d2e3f4a5b");

    private final Waiters waiters = new Waiters(Clock.fixed(NOW, ZoneOffset.UTC), () -> Duration.ofMinutes(3));

    @Test
    void aSecondConnectionUnderTheSameIdIsTurnedAway() {
        assertThat(waiters.hold(BOB, ID, "Bob")).isTrue();

        assertThat(waiters.hold(MALLORY, ID, "Bob")).isFalse();
    }

    @Test
    void aSecondConnectionUnderTheSameNameInAnotherCaseIsTurnedAway() {
        assertThat(waiters.hold(BOB, ID, "Bob")).isTrue();

        assertThat(waiters.hold(MALLORY, UUID.randomUUID(), "bOB")).isFalse();
    }

    @Test
    void theNameIsFreeOnceTheFirstConnectionLeft() {
        waiters.hold(BOB, ID, "Bob");

        assertThat(waiters.release(BOB)).contains(ID);
        assertThat(waiters.hold(MALLORY, ID, "Bob")).isTrue();
    }

    @Test
    void onlyAnotherConnectionHoldsAnId() {
        waiters.hold(BOB, ID, "Bob");

        assertThat(waiters.heldByAnother(MALLORY, ID)).isTrue();
        assertThat(waiters.heldByAnother(BOB, ID)).isFalse();
    }

    @Test
    void aHoldEndsAfterTheLongestWait() {
        Instant[] now = {NOW};
        Waiters timed = new Waiters(
                new Clock() {
                    @Override
                    public ZoneOffset getZone() {
                        return ZoneOffset.UTC;
                    }

                    @Override
                    public Clock withZone(java.time.ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return now[0];
                    }
                },
                () -> Duration.ofMinutes(3));
        timed.hold(BOB, ID, "Bob");
        now[0] = NOW.plus(Duration.ofMinutes(4));

        assertThat(timed.heldByAnother(MALLORY, ID)).isFalse();
        assertThat(timed.hold(MALLORY, ID, "Bob")).isTrue();
    }
}
