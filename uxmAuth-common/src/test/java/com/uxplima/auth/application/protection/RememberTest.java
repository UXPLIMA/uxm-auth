package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.testing.KeptSessions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Who skips the password: a player back from the same address within the time, when the operator allows it. */
final class RememberTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final String HOME = "203.0.113.7";
    private static final String FILTER = "198.51.100.20";
    private static final String PLAY = "play.example.org";
    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    private final KeptSessions kept = new KeptSessions();
    private Remember.Mode mode = Remember.Mode.CHOSEN;
    private Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private Remember remember() {
        return new Remember(
                kept,
                () -> new Remember.Settings(mode, Duration.ofMinutes(60)),
                () -> new SharedAddresses(
                        new HostPatterns(List.of("*.tcpshield.com")), new AddressRanges(List.of("198.51.100.0/24"))),
                () -> clock.instant());
    }

    @Test
    @DisplayName("with sessions for everybody, a login is remembered for the time and resumed from the same address")
    void everybodyIsRemembered() {
        mode = Remember.Mode.EVERYONE;
        remember().loggedIn(STEVE, HOME, PLAY);

        assertThat(remember().resumes(STEVE, HOME, PLAY)).isTrue();
        assertThat(remember().resumes(STEVE, "198.51.100.2", PLAY)).isFalse();
        clock = Clock.fixed(NOW.plus(Duration.ofMinutes(61)), ZoneOffset.UTC);
        assertThat(remember().resumes(STEVE, HOME, PLAY)).isFalse();
    }

    @Test
    @DisplayName("with sessions for those who choose, only a player who chose is remembered")
    void onlyTheChosenAreRemembered() {
        remember().loggedIn(STEVE, HOME, PLAY);
        assertThat(remember().resumes(STEVE, HOME, PLAY)).isFalse();

        remember().choose(STEVE, true);
        remember().loggedIn(STEVE, HOME, PLAY);
        assertThat(remember().resumes(STEVE, HOME, PLAY)).isTrue();
    }

    @Test
    @DisplayName("choosing no sessions ends the ones the account has, and sessions off remembers nobody")
    void choosingOffEndsThem() {
        remember().choose(STEVE, true);
        remember().loggedIn(STEVE, HOME, PLAY);

        remember().choose(STEVE, false);
        assertThat(remember().resumes(STEVE, HOME, PLAY)).isFalse();

        mode = Remember.Mode.OFF;
        remember().choose(STEVE, true);
        remember().loggedIn(STEVE, HOME, PLAY);
        assertThat(kept.until).isEmpty();
        mode = Remember.Mode.EVERYONE;
        kept.remember(STEVE, HOME, NOW.plusSeconds(60));
        mode = Remember.Mode.OFF;
        assertThat(remember().resumes(STEVE, HOME, PLAY)).isFalse();
    }

    @Test
    @DisplayName("through a shared-address filter nobody is remembered and nobody resumes")
    void aSharedHostRemembersNobody() {
        mode = Remember.Mode.EVERYONE;
        remember().loggedIn(STEVE, FILTER, "mc.tcpshield.com");
        assertThat(kept.until).isEmpty();

        kept.remember(STEVE, FILTER, NOW.plusSeconds(600));
        assertThat(remember().resumes(STEVE, FILTER, "mc.tcpshield.com")).isFalse();
    }

    @Test
    @DisplayName("the filter's host typed from a home address is an ordinary connection, remembered by that address")
    void aTypedFilterHostIsOrdinary() {
        mode = Remember.Mode.EVERYONE;
        remember().loggedIn(STEVE, HOME, "mc.tcpshield.com");

        assertThat(remember().resumes(STEVE, HOME, "mc.tcpshield.com")).isTrue();
    }

    @Test
    @DisplayName("forgetting an account ends its sessions")
    void forgettingEndsThem() {
        mode = Remember.Mode.EVERYONE;
        remember().loggedIn(STEVE, HOME, PLAY);

        remember().forget(STEVE);

        assertThat(remember().resumes(STEVE, HOME, PLAY)).isFalse();
    }

    @Test
    @DisplayName("from the filter's address a host nobody knows counts as the filter's; from a home it is ordinary")
    void anUnknownHostCountsAsShared() {
        mode = Remember.Mode.EVERYONE;
        remember().loggedIn(STEVE, FILTER, "");
        assertThat(kept.until).isEmpty();

        remember().loggedIn(STEVE, HOME, "");
        assertThat(remember().resumes(STEVE, HOME, "")).isTrue();
    }

    @Test
    @DisplayName("with no shared-address host listed, a host nobody knows is an ordinary one")
    void anUnknownHostWithoutSharedHosts() {
        mode = Remember.Mode.EVERYONE;
        Remember plain = new Remember(
                kept,
                () -> new Remember.Settings(mode, Duration.ofMinutes(60)),
                SharedAddresses::none,
                () -> clock.instant());

        plain.loggedIn(STEVE, HOME, "");

        assertThat(plain.resumes(STEVE, HOME, "")).isTrue();
    }

    @Test
    @DisplayName("a choice of no sessions that lands while a login is written leaves no session behind")
    void aChoiceMadeDuringTheWriteWins() {
        kept.choose(STEVE, true);
        RememberedSessions turnedOffMidway = new RememberedSessions() {
            @Override
            public void remember(UUID account, String address, Instant until) {
                // "/session off" chose no sessions after the choice was read and before this write, and its
                // forget already ran.
                kept.choose(account, false);
                kept.remember(account, address, until);
            }

            @Override
            public boolean holds(UUID account, String address, Instant now) {
                return kept.holds(account, address, now);
            }

            @Override
            public void forget(UUID account) {
                kept.forget(account);
            }

            @Override
            public java.util.Optional<Boolean> choice(UUID account) {
                return kept.choice(account);
            }

            @Override
            public void choose(UUID account, boolean remember) {
                kept.choose(account, remember);
            }
        };
        Remember remember = new Remember(
                turnedOffMidway,
                () -> new Remember.Settings(mode, Duration.ofMinutes(60)),
                SharedAddresses::none,
                () -> clock.instant());

        remember.loggedIn(STEVE, HOME, PLAY);

        assertThat(remember.resumes(STEVE, HOME, PLAY)).isFalse();
    }
}
