package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.state.AuthMethod;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** The proxy takes a server's report only from a server, about the player on that connection, signed. */
final class ReportListenerTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-17T20:00:00Z"), ZoneOffset.UTC);
    private final Claims claims = new Claims(NetworkSecret.parse("q".repeat(43)), Duration.ofSeconds(30));
    private final ProxySessions sessions = new ProxySessions();
    private final Logger logger = mock(Logger.class);
    private final ReportListener listener = new ReportListener(sessions, () -> Optional.of(claims), clock, logger);
    private final Player steve = mock(Player.class);
    private final ServerConnection server = mock(ServerConnection.class);

    ReportListenerTest() {
        when(steve.getUniqueId()).thenReturn(STEVE);
        when(steve.getUsername()).thenReturn("Steve");
        when(server.getPlayer()).thenReturn(steve);
    }

    private PluginMessageEvent report(Object source, UUID about, String name, Claim.Standing standing) {
        byte[] data = claims.sign(new Claim(about, name, standing, clock.instant(), "n0nce"))
                .getBytes(StandardCharsets.UTF_8);
        PluginMessageEvent event = new PluginMessageEvent(
                (com.velocitypowered.api.proxy.messages.ChannelMessageSource) source,
                mock(com.velocitypowered.api.proxy.messages.ChannelMessageSink.class),
                MinecraftChannelIdentifier.from(Claims.CHANNEL),
                data);
        listener.read(event);
        return event;
    }

    @Test
    @DisplayName("a server's signed login logs the player in on the proxy, and the network already greeted it")
    void aLoginReportLogsIn() {
        PluginMessageEvent event = report(server, STEVE, "Steve", new Claim.LoggedIn(AuthMethod.PASSWORD));

        assertThat(sessions.loggedIn(STEVE)).isTrue();
        assertThat(sessions.nextStanding(STEVE)).contains(new Claim.LoggedIn(AuthMethod.NETWORK));
        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a server's signed wait puts the player back to waiting")
    void aWaitReportWaits() {
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);

        report(server, STEVE, "Steve", new Claim.Waiting(false, false, false));

        assertThat(sessions.loggedIn(STEVE)).isFalse();
    }

    @Test
    @DisplayName("a server's wait after a login gives back the door's own flags, not the server's")
    void aWaitKeepsTheDoorsFlags() {
        sessions.waiting(STEVE, new Claim.Waiting(true, false, true));
        report(server, STEVE, "Steve", new Claim.LoggedIn(AuthMethod.PASSWORD));

        report(server, STEVE, "Steve", new Claim.Waiting(false, false, false));

        assertThat(sessions.nextStanding(STEVE)).contains(new Claim.Waiting(true, false, true));
    }

    @Test
    @DisplayName("the channel from a client is dropped and never passed on, whatever it says")
    void aClientsReportIsDropped() {
        PluginMessageEvent event = report(steve, STEVE, "Steve", new Claim.LoggedIn(AuthMethod.PASSWORD));

        assertThat(sessions.loggedIn(STEVE)).isFalse();
        assertThat(event.getResult().isAllowed()).isFalse();
    }

    @Test
    @DisplayName("a report about another player than the one on that connection is refused, and the console says why")
    void aReportAboutAnotherPlayerIsRefused() {
        report(server, UUID.randomUUID(), "Alex", new Claim.LoggedIn(AuthMethod.PASSWORD));

        assertThat(sessions.loggedIn(STEVE)).isFalse();
        org.mockito.Mockito.verify(logger)
                .warn(
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("Steve"),
                        org.mockito.ArgumentMatchers.eq("NOT_THIS_PLAYER"));
    }

    @Test
    @DisplayName("a message on another channel is left for whoever it is for")
    void anotherChannelIsLeftAlone() {
        PluginMessageEvent event = new PluginMessageEvent(
                server,
                mock(com.velocitypowered.api.proxy.messages.ChannelMessageSink.class),
                MinecraftChannelIdentifier.from("other:channel"),
                new byte[0]);

        listener.read(event);

        assertThat(event.getResult().isAllowed()).isTrue();
    }
}
