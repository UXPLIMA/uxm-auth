package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.AddressTallies;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import org.junit.jupiter.api.Test;

class AddressBansTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final BanSteps STEPS = new BanSteps(List.of(Duration.ofMinutes(5)), 3, Duration.ofHours(24));
    private static final BanSteps WIDE = new BanSteps(List.of(Duration.ofMinutes(5)), 4, Duration.ofHours(24));

    private final MemoryTallies tallies = new MemoryTallies();
    private final AddressBans bans =
            new AddressBans(tallies, () -> STEPS, () -> WIDE, SharedAddresses::none, () -> NOW);

    @Test
    void hoppingBetweenTheSixtyFoursOfOneFortyEightStillMeetsABan() {
        assertThat(bans.failed("2001:db8:1:1::7", "")).isEmpty();
        assertThat(bans.failed("2001:db8:1:2::7", "")).isEmpty();
        assertThat(bans.failed("2001:db8:1:3::7", "")).isEmpty();

        assertThat(bans.failed("2001:db8:1:4::7", "")).contains(Duration.ofMinutes(5));
        assertThat(bans.rule().check(new Admission.Arrival("2001:db8:1:ffff::1", "")))
                .isPresent();
        assertThat(bans.rule().check(new Admission.Arrival("2001:db8:2::1", "")))
                .isEmpty();
    }

    @Test
    void anUnbanLiftsTheFortyEightToo() {
        for (int sixtyFour = 1; sixtyFour <= 4; sixtyFour++) {
            bans.failed("2001:db8:1:" + sixtyFour + "::7", "");
        }

        assertThat(bans.unban("2001:db8:1:9::1")).isEqualTo(AddressBans.Unban.LIFTED);
        assertThat(bans.rule().check(new Admission.Arrival("2001:db8:1:9::1", "")))
                .isEmpty();
    }

    @Test
    void anIpv4AddressCountsAlone() {
        bans.failed("203.0.113.7", "");
        bans.failed("203.0.113.7", "");

        assertThat(bans.failed("203.0.113.7", "")).contains(Duration.ofMinutes(5));
        assertThat(bans.rule().check(new Admission.Arrival("203.0.113.8", ""))).isEmpty();
    }

    /** The tallies in a map. */
    private static final class MemoryTallies implements AddressTallies {
        private final Map<String, AddressTally> kept = new ConcurrentHashMap<>();

        @Override
        public Optional<AddressTally> find(String key) {
            return Optional.ofNullable(kept.get(key));
        }

        @Override
        public AddressTally change(String key, UnaryOperator<AddressTally> change) {
            return kept.compute(key, (k, before) -> change.apply(before == null ? AddressTally.clean() : before));
        }

        @Override
        public void clear(String key) {
            kept.remove(key);
        }
    }
}
