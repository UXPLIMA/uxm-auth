package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryDisputes;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** "This name is mine": a claim from the door, or the owner's own request from inside the account. */
final class ClaimNameTest {

    private static final String HERE = "10.0.0.7";

    private final AuthFixture fixture = new AuthFixture(1);
    private final TurnedClock clock = new TurnedClock(AuthFixture.NOW);
    private NameRule rule = NameRule.AUTOMATIC;
    private final AtTheDoor door = new AtTheDoor(
            fixture.accounts,
            () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
            new PremiumLookup(List::of, () -> Duration.ofMinutes(30), clock),
            () -> new NameOwnership(
                    rule, WhenUnknown.CRACKED, new HostPatterns(List.of("p.example.org")), new HostPatterns(List.of())),
            new NameMemory(() -> Duration.ofMinutes(5), clock),
            new NameMemory(() -> Duration.ofMinutes(10), clock),
            new NameMemory(() -> Duration.ofMinutes(10), clock),
            new InMemoryDisputes());
    private final ClaimName claim = new ClaimName(fixture.sessions, door, () -> rule);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a waiting player's claim asks their next connection from here for the handshake")
    void aWaitingPlayersClaimAsksTheirNextConnection() {
        UUID connection = fixture.registered("Notch", "hunter22");

        assertThat(claim.claim(connection, HERE)).isEqualTo(ClaimName.Result.ASKED);

        assertThat(door.decide("Notch", HERE, "h")).isEqualTo(new Decision.Handshake());
        assertThat(door.ownerAsked(HERE, new PlayerName("Notch"))).isFalse();
    }

    @Test
    @DisplayName("a logged-in owner's request turns their own account premium on the next connection")
    void aLoggedInOwnersRequestIsTheOwners() {
        UUID connection = fixture.registered("Notch", "hunter22");
        Account account = fixture.accounts.findByName(new PlayerName("Notch")).orElseThrow();
        fixture.sessions.open(connection, new AuthState.Authenticated(account, AuthMethod.PASSWORD));

        assertThat(claim.claim(connection, HERE)).isEqualTo(ClaimName.Result.ASKED);

        assertThat(door.decide("Notch", HERE, "h")).isEqualTo(new Decision.Handshake());
        assertThat(door.ownerAsked(HERE, new PlayerName("Notch"))).isTrue();
    }

    @Test
    @DisplayName("a premium account has nothing to claim")
    void aPremiumAccountHasNothingToClaim() {
        UUID connection = UUID.randomUUID();
        Account premium = new Account(
                connection,
                new PlayerName("Notch"),
                AccountKind.PREMIUM,
                connection,
                null,
                AuthFixture.NOW,
                HERE,
                null,
                null);
        fixture.sessions.open(connection, new AuthState.Authenticated(premium, AuthMethod.PREMIUM));

        assertThat(claim.claim(connection, HERE)).isEqualTo(ClaimName.Result.ALREADY_PREMIUM);
    }

    @Test
    @DisplayName("under strict and by domain a claim is not how a name is proved, and nothing is asked")
    void strictAndDomainNeedNoClaim() {
        UUID connection = fixture.registered("Notch", "hunter22");

        rule = NameRule.STRICT;
        assertThat(claim.claim(connection, HERE)).isEqualTo(ClaimName.Result.NOT_THIS_RULE);
        rule = NameRule.DOMAIN;
        assertThat(claim.claim(connection, HERE)).isEqualTo(ClaimName.Result.NOT_THIS_RULE);
    }

    @Test
    @DisplayName("a connection with no session claims nothing")
    void noSessionClaimsNothing() {
        assertThat(claim.claim(UUID.randomUUID(), HERE)).isEqualTo(ClaimName.Result.GONE);
    }
}
