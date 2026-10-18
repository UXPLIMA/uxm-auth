package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.AddressTallies;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Wrong passwords count against the address they came from, and a banned address is refused at the door. */
final class AddressBansTest {

    private static final Instant START = Instant.parse("2026-10-18T10:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final String PLAY = "play.example.org";
    private static final String SHIELD = "mc.tcpshield.com";

    private final Map<String, AddressTally> kept = new ConcurrentHashMap<>();
    private final AddressTallies tallies = new AddressTallies() {
        @Override
        public Optional<AddressTally> find(String key) {
            return Optional.ofNullable(kept.get(key));
        }

        @Override
        public AddressTally change(String key, UnaryOperator<AddressTally> change) {
            return kept.compute(key, (k, tally) -> change.apply(tally == null ? AddressTally.clean() : tally));
        }

        @Override
        public void clear(String key) {
            kept.remove(key);
        }
    };
    private Instant now = START;

    private AddressBans bans() {
        return new AddressBans(
                tallies,
                () -> new BanSteps(List.of(Duration.ofMinutes(5), Duration.ofMinutes(30)), 3, Duration.ofHours(24)),
                () -> new HostPatterns(List.of("*.tcpshield.com")),
                () -> now);
    }

    private Admission door() {
        return new Admission(() -> new HostPatterns(List.of("*.tcpshield.com")), List.of(bans().rule()));
    }

    @Test
    @DisplayName(
            "the failure that reaches the limit answers the ban, and the door refuses the address with the time left")
    void theLimitBansAndTheDoorRefuses() {
        assertThat(bans().failed(HOME, PLAY)).isEmpty();
        assertThat(bans().failed(HOME, PLAY)).isEmpty();
        assertThat(bans().failed(HOME, PLAY)).contains(Duration.ofMinutes(5));

        now = START.plus(Duration.ofMinutes(2));
        assertThat(door().decide(new Admission.Arrival(HOME, PLAY)))
                .contains(new Admission.Refused(Keys.KICK_BANNED, Map.of(), Duration.ofMinutes(3)));
        assertThat(door().decide(new Admission.Arrival("198.51.100.2", PLAY))).isEmpty();
    }

    @Test
    @DisplayName("a ban that ran out by the time the player connects lets them in")
    void aBanThatRanOutLetsIn() {
        for (int i = 0; i < 3; i++) {
            bans().failed(HOME, PLAY);
        }

        now = START.plus(Duration.ofMinutes(5));

        assertThat(door().decide(new Admission.Arrival(HOME, PLAY))).isEmpty();
    }

    @Test
    @DisplayName("through a shared-address host a failure counts against nobody, so one player cannot ban the rest")
    void aSharedHostCountsNothing() {
        for (int i = 0; i < 5; i++) {
            assertThat(bans().failed(HOME, SHIELD)).isEmpty();
        }

        assertThat(kept).isEmpty();
        assertThat(door().decide(new Admission.Arrival(HOME, PLAY))).isEmpty();
    }

    @Test
    @DisplayName("an IPv6 home counts as one address across its /64")
    void anIpv6HomeCountsAsOne() {
        bans().failed("2001:db8:1:2::1", PLAY);
        bans().failed("2001:db8:1:2::2", PLAY);
        assertThat(bans().failed("2001:db8:1:2::3", PLAY)).isPresent();

        assertThat(door().decide(new Admission.Arrival("2001:db8:1:2::99", PLAY)))
                .isPresent();
    }
}
