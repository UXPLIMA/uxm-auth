package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Wrong passwords from one address ban it for the operator's steps, longer each time, until a quiet spell. */
final class AddressTallyTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final BanSteps SHIPPED = new BanSteps(
            List.of(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofDays(1)),
            3,
            Duration.ofHours(24));

    private static AddressTally fail(AddressTally tally, Instant at, int times) {
        AddressTally after = tally;
        for (int i = 0; i < times; i++) {
            after = after.failedAt(at, SHIPPED);
        }
        return after;
    }

    @Test
    @DisplayName("the failures before the limit only count; the one that reaches it bans for the first step")
    void theLimitBansForTheFirstStep() {
        AddressTally two = fail(AddressTally.clean(), NOW, 2);
        assertThat(two.failures()).isEqualTo(2);
        assertThat(two.bannedAt(NOW)).isFalse();

        AddressTally banned = two.failedAt(NOW, SHIPPED);

        assertThat(banned.bannedAt(NOW)).isTrue();
        assertThat(banned.leftAt(NOW)).isEqualTo(Duration.ofMinutes(5));
        assertThat(banned.failures()).isZero();
        assertThat(banned.step()).isEqualTo(1);
    }

    @Test
    @DisplayName("every ban after the first is the next step, and the last step repeats")
    void theStepsClimbAndStopAtTheLast() {
        AddressTally tally = AddressTally.clean();
        Instant at = NOW;
        List<Duration> seen = new java.util.ArrayList<>();
        for (int ban = 0; ban < 6; ban++) {
            tally = fail(tally, at, 3);
            seen.add(tally.leftAt(at));
            at = at.plus(tally.leftAt(at)).plusSeconds(1);
        }

        assertThat(seen)
                .containsExactly(
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(30),
                        Duration.ofHours(1),
                        Duration.ofDays(1),
                        Duration.ofDays(1),
                        Duration.ofDays(1));
    }

    @Test
    @DisplayName("a ban runs out at its end, to the second")
    void aBanRunsOut() {
        AddressTally banned = fail(AddressTally.clean(), NOW, 3);

        assertThat(banned.bannedAt(NOW.plus(Duration.ofMinutes(5)).minusMillis(1)))
                .isTrue();
        assertThat(banned.bannedAt(NOW.plus(Duration.ofMinutes(5)))).isFalse();
        assertThat(banned.leftAt(NOW.plus(Duration.ofMinutes(6)))).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("a quiet spell as long as forgive-after, from the last failure or the end of the ban, starts again")
    void aQuietSpellForgives() {
        AddressTally once = fail(AddressTally.clean(), NOW, 3);
        Instant banEnds = NOW.plus(Duration.ofMinutes(5));

        AddressTally almost = once.failedAt(banEnds.plus(Duration.ofHours(24)).minusSeconds(1), SHIPPED);
        assertThat(almost.step()).isEqualTo(1);
        assertThat(almost.failures()).isEqualTo(1);

        AddressTally forgiven = once.failedAt(banEnds.plus(Duration.ofHours(24)), SHIPPED);
        assertThat(forgiven.step()).isZero();
        assertThat(forgiven.failures()).isEqualTo(1);

        AddressTally counting = fail(AddressTally.clean(), NOW, 2);
        AddressTally freshCount = counting.failedAt(NOW.plus(Duration.ofHours(24)), SHIPPED);
        assertThat(freshCount.failures()).isEqualTo(1);
        assertThat(freshCount.bannedAt(NOW.plus(Duration.ofHours(24)))).isFalse();
    }

    @Test
    @DisplayName("the steps need at least one positive length, a limit of one or more, and a positive quiet spell")
    void theStepsAreChecked() {
        assertThatThrownBy(() -> new BanSteps(List.of(), 3, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BanSteps(List.of(Duration.ZERO), 3, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BanSteps(List.of(Duration.ofMinutes(1)), 0, Duration.ofHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BanSteps(List.of(Duration.ofMinutes(1)), 3, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an IPv4 address is its own key, an IPv6 address counts by its /64, as one home holds the whole /64")
    void theKeyOfAnAddress() {
        assertThat(AddressKey.of("203.0.113.7")).isEqualTo("203.0.113.7");
        assertThat(AddressKey.of("2001:db8:1:2:aaaa::1")).isEqualTo(AddressKey.of("2001:0db8:0001:0002:ffff:1:2:3"));
        assertThat(AddressKey.of("2001:db8:1:2::1")).isEqualTo("2001:db8:1:2::/64");
        assertThat(AddressKey.of("2001:db8:1:3::1")).isNotEqualTo(AddressKey.of("2001:db8:1:2::1"));
        assertThat(AddressKey.of("::ffff:203.0.113.7")).isEqualTo("203.0.113.7");
    }
}
