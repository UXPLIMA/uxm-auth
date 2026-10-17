package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mojang answers 600 requests in ten minutes; the cap stops asking before that. */
final class RequestCapTest {

    private final TurnedClock clock = new TurnedClock(AuthFixture.NOW);

    @Test
    @DisplayName("the cap lets as many requests through in ten minutes as the setting says, and no more")
    void theCapStopsAtTheSetting() {
        RequestCap cap = new RequestCap(() -> 3, clock);

        assertThat(cap.take()).isTrue();
        assertThat(cap.take()).isTrue();
        assertThat(cap.take()).isTrue();
        assertThat(cap.take()).isFalse();
    }

    @Test
    @DisplayName("a request leaves the count ten minutes after it was made")
    void aRequestLeavesTheCountAfterTenMinutes() {
        RequestCap cap = new RequestCap(() -> 2, clock);
        cap.take();
        clock.turn(Duration.ofMinutes(5));
        cap.take();

        clock.turn(Duration.ofMinutes(5).minusSeconds(1));
        assertThat(cap.take()).isFalse();
        clock.turn(Duration.ofSeconds(1));
        assertThat(cap.take()).isTrue();
        assertThat(cap.take()).isFalse();
    }

    @Test
    @DisplayName("a refused request does not count")
    void aRefusedRequestDoesNotCount() {
        RequestCap cap = new RequestCap(() -> 1, clock);
        cap.take();
        clock.turn(Duration.ofMinutes(9));
        cap.take();

        clock.turn(Duration.ofMinutes(1));
        assertThat(cap.take()).isTrue();
    }
}
