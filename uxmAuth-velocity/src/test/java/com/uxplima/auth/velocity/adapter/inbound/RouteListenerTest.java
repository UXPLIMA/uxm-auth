package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.SqlLastServers;
import com.uxplima.auth.domain.network.AfterLogin;
import com.uxplima.auth.domain.network.Balance;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The proxy sends a waiting player to an auth server, and a logged-in one where the operator's rule says. */
final class RouteListenerTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final ProxyWorld world;
    private final SqlLastServers lastServers;
    private final ProxySessions sessions = new ProxySessions();
    private final Map<String, RegisteredServer> servers = new HashMap<>();
    private final Map<String, Integer> players = new HashMap<>();
    private final Player steve = mock(Player.class);
    private AfterLogin after = AfterLogin.LOBBY;
    private List<String> authServers = List.of("auth-1", "auth-2");

    RouteListenerTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        this.lastServers = new SqlLastServers(world.database);
        when(steve.getUniqueId()).thenReturn(STEVE);
        when(steve.getVirtualHost())
                .thenReturn(Optional.of(InetSocketAddress.createUnresolved("play.example.org", 25565)));
        for (String name : List.of("auth-1", "auth-2", "lobby-1", "lobby-2", "survival")) {
            RegisteredServer server = mock(RegisteredServer.class);
            when(server.getServerInfo())
                    .thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved("localhost", 1)));
            servers.put(name, server);
        }
        players.putAll(Map.of("auth-1", 7, "auth-2", 3, "lobby-1", 4, "lobby-2", 1, "survival", 0));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private RouteListener listener() {
        AuthSettings.Routing routing = new AuthSettings.Routing(
                authServers, List.of("lobby-1", "lobby-2"), Balance.LEAST_PLAYERS, after, List.of());
        return new RouteListener(
                sessions,
                () -> routing,
                lastServers,
                name -> Optional.ofNullable(servers.get(name)),
                () -> players,
                RandomGenerator.of("L64X128MixRandom"));
    }

    private Optional<String> firstServer() {
        PlayerChooseInitialServerEvent event = new PlayerChooseInitialServerEvent(steve, servers.get("survival"));
        listener().initial(event);
        return event.getInitialServer().map(server -> server.getServerInfo().getName());
    }

    private void standingOn(String name) {
        ServerConnection connection = mock(ServerConnection.class);
        when(connection.getServer()).thenReturn(servers.get(name));
        when(steve.getCurrentServer()).thenReturn(Optional.of(connection));
    }

    @Test
    @DisplayName(
            "a waiting player goes first to the emptiest auth server, and keeps the proxy's choice when none is up")
    void aWaitingPlayerGoesToAnAuthServer() {
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        assertThat(firstServer()).contains("auth-2");

        players.remove("auth-1");
        players.remove("auth-2");
        assertThat(firstServer()).contains("survival");

        authServers = List.of();
        assertThat(firstServer()).contains("survival");
    }

    @Test
    @DisplayName("a player Mojang logged in goes first where the rule after login says: a lobby, or their last server")
    void aLoggedInPlayerGoesWhereTheRuleSays() {
        sessions.loggedIn(STEVE, AuthMethod.PREMIUM);
        assertThat(firstServer()).contains("lobby-2");

        after = AfterLogin.LAST_SERVER;
        lastServers.remember(STEVE, "survival");
        assertThat(firstServer()).contains("survival");
    }

    @Test
    @DisplayName("a login on an auth server sends the player on to a lobby; a login elsewhere leaves them there")
    void aLoginOnAnAuthServerMovesThePlayer() {
        ConnectionRequestBuilder request = mock(ConnectionRequestBuilder.class);
        when(steve.createConnectionRequest(any())).thenReturn(request);
        standingOn("auth-1");

        listener().loggedIn(steve);
        verify(steve).createConnectionRequest(servers.get("lobby-2"));
        verify(request).fireAndForget();

        RouteListener elsewhere = listener();
        standingOn("survival");
        org.mockito.Mockito.clearInvocations(steve);
        elsewhere.loggedIn(steve);
        verify(steve, never()).createConnectionRequest(any());
    }

    @Test
    @DisplayName("the server a logged-in player reaches is remembered; an auth server and a waiting player's are not")
    void theLastServerIsRemembered() {
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);
        listener().connected(new ServerConnectedEvent(steve, servers.get("survival"), null));
        assertThat(lastServers.of(STEVE)).contains("survival");

        listener().connected(new ServerConnectedEvent(steve, servers.get("auth-1"), null));
        assertThat(lastServers.of(STEVE)).contains("survival");

        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        listener().connected(new ServerConnectedEvent(steve, servers.get("lobby-1"), null));
        assertThat(lastServers.of(STEVE)).contains("survival");
    }

    @Test
    @DisplayName("an auth server is a waiting room: a waiting player may be sent there")
    void anAuthServerIsAWaitingRoom() {
        assertThat(listener().waitingRoom("auth-2")).isTrue();
        assertThat(listener().waitingRoom("lobby-1")).isFalse();
        assertThat(new ArrayList<>(authServers)).hasSize(2);
    }
}
