package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Before every connection to a server the proxy writes a fresh claim into the profile it forwards. */
final class ClaimWriterTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final GameProfile.Property SKIN = new GameProfile.Property("textures", "skin", "sig");

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"), ZoneOffset.UTC);
    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30));
    private final ProxySessions sessions = new ProxySessions();
    private final ClaimWriter writer = new ClaimWriter(sessions, () -> Optional.of(claims), clock);
    private final Player player = mock(Player.class);
    private List<GameProfile.Property> properties = new ArrayList<>(List.of(SKIN));

    ClaimWriterTest() {
        when(player.getUniqueId()).thenReturn(STEVE);
        when(player.getUsername()).thenReturn("Steve");
        when(player.getGameProfileProperties()).thenAnswer(ignored -> properties);
        doAnswer(call -> {
                    properties = new ArrayList<>(call.<List<GameProfile.Property>>getArgument(0));
                    return null;
                })
                .when(player)
                .setGameProfileProperties(any());
    }

    private Claims.Reading written() {
        String value = properties.stream()
                .filter(property -> property.getName().equals(ClaimWriter.PROPERTY))
                .map(GameProfile.Property::getValue)
                .findFirst()
                .orElseThrow();
        return claims.read(value, STEVE, "Steve", clock.instant());
    }

    @Test
    @DisplayName("a premium login is vouched for as premium on the first server and as the network's after")
    void aLoginIsGreetedOnce() {
        sessions.loggedIn(STEVE, AuthMethod.PREMIUM);

        writer.claim(player);
        assertThat(written())
                .isInstanceOfSatisfying(
                        Claims.Reading.Valid.class,
                        valid ->
                                assertThat(valid.claim().standing()).isEqualTo(new Claim.LoggedIn(AuthMethod.PREMIUM)));
        writer.claim(player);
        assertThat(written())
                .isInstanceOfSatisfying(
                        Claims.Reading.Valid.class,
                        valid ->
                                assertThat(valid.claim().standing()).isEqualTo(new Claim.LoggedIn(AuthMethod.NETWORK)));
    }

    @Test
    @DisplayName("a waiting player is vouched for with the door's flags, and the skin stays beside the claim")
    void aWaitingPlayerCarriesTheFlags() {
        sessions.waiting(STEVE, new Claim.Waiting(true, false, true));

        writer.claim(player);

        assertThat(written())
                .isInstanceOfSatisfying(
                        Claims.Reading.Valid.class,
                        valid -> assertThat(valid.claim().standing()).isEqualTo(new Claim.Waiting(true, false, true)));
        assertThat(properties).contains(SKIN).hasSize(2);
    }

    @Test
    @DisplayName("a player who owes the app's code is vouched for as owing it, by the way the door let them past")
    void aPlayerOwingTheCodeIsVouchedForAsOwing() {
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        sessions.code(STEVE, AuthMethod.SESSION);

        writer.claim(player);

        assertThat(written())
                .isInstanceOfSatisfying(
                        Claims.Reading.Valid.class,
                        valid -> assertThat(valid.claim().standing()).isEqualTo(new Claim.Code(AuthMethod.SESSION)));
        assertThat(sessions.loggedIn(STEVE)).isFalse();
        assertThat(sessions.waitsAtTheProxy(STEVE)).isTrue();
        assertThat(sessions.passed(STEVE)).contains(AuthMethod.SESSION);
        sessions.loggedIn(STEVE, AuthMethod.SESSION);
        assertThat(sessions.passed(STEVE)).isEmpty();
    }

    @Test
    @DisplayName("one claim at a time: a claim left from an earlier connection is replaced, and none is kept unknown")
    void anOldClaimIsReplaced() {
        properties.add(new GameProfile.Property(ClaimWriter.PROPERTY, "stale", ""));
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        writer.claim(player);
        assertThat(properties.stream().filter(property -> property.getName().equals(ClaimWriter.PROPERTY)))
                .hasSize(1);

        sessions.forget(STEVE);
        writer.claim(player);
        assertThat(properties).containsExactly(SKIN);
    }

    @Test
    @DisplayName("a proxy with no secret writes no claim, so every server asks for the password")
    void noSecretNoClaim() {
        sessions.loggedIn(STEVE, AuthMethod.PREMIUM);

        new ClaimWriter(sessions, Optional::empty, clock).claim(player);

        assertThat(properties).containsExactly(SKIN);
    }

    @Test
    @DisplayName("the visit a claim names becomes the player's once the server lets them in, and not before")
    void theClaimsVisitIsThePlayers() {
        sessions.loggedIn(STEVE, AuthMethod.PREMIUM);

        writer.claim(player);
        String nonce = ((Claims.Reading.Valid) written()).claim().nonce();
        assertThat(sessions.visit(STEVE)).isEmpty();

        sessions.arrived(STEVE);
        assertThat(sessions.visit(STEVE)).contains(nonce);
    }
}
