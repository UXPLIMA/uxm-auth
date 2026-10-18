package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.protection.AddressRanges;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Connections from one address are counted inside a sliding window, whatever comes of each, and capped. */
final class ConnectionWindowTest {

    private static final Instant START = Instant.parse("2026-10-18T10:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final String PLAY = "play.example.org";

    private Instant now = START;
    private ConnectionWindow.Settings settings =
            new ConnectionWindow.Settings(3, Duration.ofSeconds(60), new AddressRanges(List.of("10.0.0.0/8")));
    private final ConnectionWindow window = new ConnectionWindow(() -> settings, () -> now);

    private java.util.Optional<Admission.Refused> arrive(String address) {
        return window.check(new Admission.Arrival(address, PLAY));
    }

    @Test
    @DisplayName("connections up to the limit pass, the next one inside the window is refused with the time left")
    void theLimitInsideTheWindow() {
        assertThat(arrive(HOME)).isEmpty();
        now = START.plusSeconds(10);
        assertThat(arrive(HOME)).isEmpty();
        now = START.plusSeconds(20);
        assertThat(arrive(HOME)).isEmpty();
        now = START.plusSeconds(30);

        assertThat(arrive(HOME))
                .contains(new Admission.Refused(Keys.KICK_TOO_MANY_CONNECTIONS, Map.of(), Duration.ofSeconds(30)));
        assertThat(arrive("198.51.100.2")).isEmpty();
    }

    @Test
    @DisplayName("the window slides: once the oldest connection is a window old, one more passes")
    void theWindowSlides() {
        for (int i = 0; i < 3; i++) {
            arrive(HOME);
        }
        now = START.plusSeconds(59);
        assertThat(arrive(HOME)).isPresent();

        now = START.plusSeconds(60);
        assertThat(arrive(HOME)).isEmpty();
    }

    @Test
    @DisplayName("an address the operator exempts is never counted")
    void anExemptAddressIsNotCounted() {
        for (int i = 0; i < 10; i++) {
            assertThat(arrive("10.1.2.3")).isEmpty();
        }
    }

    @Test
    @DisplayName("an address that keeps knocking stays refused, and what it keeps is bounded by the limit")
    void aFloodStaysRefusedAndBounded() {
        for (int i = 0; i < 1000; i++) {
            arrive(HOME);
        }

        assertThat(arrive(HOME)).isPresent();
        assertThat(window.kept(HOME)).isLessThanOrEqualTo(4);
    }

    @Test
    @DisplayName("addresses that went quiet are let go, so the window holds only recent ones")
    void quietAddressesAreLetGo() {
        arrive(HOME);
        arrive("198.51.100.2");
        now = START.plusSeconds(61);

        window.sweep();

        assertThat(window.kept(HOME)).isZero();
        assertThat(window.addresses()).isZero();
    }

    @Test
    @DisplayName("the window lets go of quiet addresses by itself as connections keep coming")
    void theWindowSweepsByItself() {
        arrive(HOME);
        now = START.plusSeconds(61);

        for (int i = 0; i < 1100; i++) {
            arrive("198.51.100.2");
        }

        assertThat(window.kept(HOME)).isZero();
        assertThat(window.addresses()).isEqualTo(1);
    }

    @Test
    @DisplayName("an IPv6 home counts as one address across its /64")
    void anIpv6HomeCountsAsOne() {
        arrive("2001:db8:1:2::1");
        arrive("2001:db8:1:2::2");
        arrive("2001:db8:1:2::3");

        assertThat(arrive("2001:db8:1:2::4")).isPresent();
    }
}
