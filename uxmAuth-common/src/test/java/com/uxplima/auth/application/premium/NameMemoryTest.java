package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a connection did lately is remembered by address and name for a short time the operator sets. */
final class NameMemoryTest {

    private final TurnedClock clock = new TurnedClock(AuthFixture.NOW);
    private final NameMemory failed = new NameMemory(() -> Duration.ofMinutes(5), clock);

    @Test
    @DisplayName("a failure is remembered for the time the operator set, then forgotten")
    void aFailureIsRememberedForTheSetTime() {
        failed.remember("10.0.0.7", new PlayerName("Notch"));

        clock.turn(Duration.ofMinutes(5).minusSeconds(1));
        assertThat(failed.holds("10.0.0.7", new PlayerName("notch"))).isTrue();
        clock.turn(Duration.ofSeconds(1));
        assertThat(failed.holds("10.0.0.7", new PlayerName("Notch"))).isFalse();
    }

    @Test
    @DisplayName("another address, or another name from the same address, did not fail")
    void onlyThatAddressAndNameFailed() {
        failed.remember("10.0.0.7", new PlayerName("Notch"));

        assertThat(failed.holds("10.0.0.8", new PlayerName("Notch"))).isFalse();
        assertThat(failed.holds("10.0.0.7", new PlayerName("Jeb_"))).isFalse();
    }

    @Test
    @DisplayName("an entry can be forgotten before its time, as a passed handshake forgets a failure")
    void aPassForgetsTheFailure() {
        failed.remember("10.0.0.7", new PlayerName("Notch"));

        failed.forget("10.0.0.7", new PlayerName("Notch"));

        assertThat(failed.holds("10.0.0.7", new PlayerName("Notch"))).isFalse();
    }

    @Test
    @DisplayName("failures past their time are let go, so a flood of them does not grow the memory")
    void oldFailuresAreLetGo() {
        for (int i = 0; i < 300; i++) {
            failed.remember("10.0.0." + (i % 250), new PlayerName("Name" + i));
        }
        clock.turn(Duration.ofMinutes(6));

        failed.remember("10.0.0.7", new PlayerName("Fresh"));

        assertThat(failed.remembered()).isEqualTo(1);
    }
}
