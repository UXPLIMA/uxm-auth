package com.uxplima.auth.paper.adapter.outbound.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** A server behind the proxy tells it of each login and each wait, signed, on the plugin channel. */
final class ChannelReportsTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"), ZoneOffset.UTC);
    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30));
    private final Plugin plugin = mock(Plugin.class);
    private final Player player = mock(Player.class);

    ChannelReportsTest() {
        when(player.getUniqueId()).thenReturn(STEVE);
        when(player.getName()).thenReturn("Steve");
    }

    private Claim.Standing sent() {
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(player).sendPluginMessage(eq(plugin), eq(Claims.CHANNEL), bytes.capture());
        Claims.Reading reading =
                claims.read(new String(bytes.getValue(), StandardCharsets.UTF_8), STEVE, "Steve", clock.instant());
        assertThat(reading).isInstanceOf(Claims.Reading.Valid.class);
        return ((Claims.Reading.Valid) reading).claim().standing();
    }

    @Test
    @DisplayName("a login is sent signed, with the way the player came in")
    void aLoginIsSent() {
        new ChannelReports(plugin, () -> Optional.of(claims), clock).loggedIn(player, AuthMethod.PASSWORD);

        assertThat(sent()).isEqualTo(new Claim.LoggedIn(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("a wait is sent signed")
    void aWaitIsSent() {
        new ChannelReports(plugin, () -> Optional.of(claims), clock).waiting(player);

        assertThat(sent()).isEqualTo(new Claim.Waiting(false, false, false));
    }

    @Test
    @DisplayName("a server that stands alone sends nothing")
    void aLoneServerSendsNothing() {
        new ChannelReports(plugin, Optional::empty, clock).loggedIn(player, AuthMethod.PASSWORD);

        verify(player, never()).sendPluginMessage(any(), any(), any());
    }
}
