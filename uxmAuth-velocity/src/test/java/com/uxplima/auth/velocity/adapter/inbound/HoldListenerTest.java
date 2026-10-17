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
import java.util.List;
import java.util.UUID;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Until a player logs in, the proxy runs none of its own commands for them and moves them to no other server. */
final class HoldListenerTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final ProxyWorld world;
    private final ProxySessions sessions = new ProxySessions();
    private final CommandManager commands = mock(CommandManager.class);
    private final Player steve = mock(Player.class);
    private final List<String> allowed = new ArrayList<>();
    private final List<String> rooms = new ArrayList<>();

    HoldListenerTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
        when(steve.getUniqueId()).thenReturn(STEVE);
        when(commands.hasCommand("glist")).thenReturn(true);
        when(commands.hasCommand("server")).thenReturn(true);
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
    }

    @AfterEach
    void close() {
        world.close();
    }

    private HoldListener listener() {
        return new HoldListener(sessions, commands, () -> allowed, rooms::contains, world.messages);
    }

    private CommandExecuteEvent typed(CommandSource source, String line) {
        CommandExecuteEvent event = new CommandExecuteEvent(source, line);
        listener().command(event);
        return event;
    }

    private static RegisteredServer server(String name) {
        RegisteredServer server = mock(RegisteredServer.class);
        when(server.getServerInfo())
                .thenReturn(new ServerInfo(name, InetSocketAddress.createUnresolved("localhost", 25565)));
        return server;
    }

    private ServerPreConnectEvent switching(String to, RegisteredServer from) {
        ServerPreConnectEvent event = new ServerPreConnectEvent(steve, server(to), from);
        listener().switched(event);
        return event;
    }

    @Test
    @DisplayName("a proxy command of a waiting player is refused with a word, one of the server's goes on to its door")
    void aProxyCommandIsRefusedWhileWaiting() {
        assertThat(typed(steve, "glist all").getResult().isAllowed()).isFalse();
        verify(steve).sendMessage(any(Component.class));

        assertThat(typed(steve, "login hunter22").getResult().isAllowed()).isTrue();
    }

    @Test
    @DisplayName("a proxy command the operator allows while waiting runs, and every command runs after login")
    void allowedAndLoggedInCommandsRun() {
        allowed.add("glist");
        assertThat(typed(steve, "glist").getResult().isAllowed()).isTrue();

        allowed.clear();
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);
        assertThat(typed(steve, "glist").getResult().isAllowed()).isTrue();
    }

    @Test
    @DisplayName("the console is never held")
    void theConsoleIsNotHeld() {
        assertThat(typed(mock(CommandSource.class), "glist").getResult().isAllowed())
                .isTrue();
    }

    @Test
    @DisplayName("a waiting player is moved to no other server, by a command or by another plugin")
    void aWaitingPlayerStays() {
        ServerPreConnectEvent event = switching("survival", server("lobby"));

        assertThat(event.getResult().isAllowed()).isFalse();
        verify(steve).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("the first server, an auth server, and every server after login are let through")
    void theseSwitchesGoThrough() {
        assertThat(new ServerPreConnectEvent(steve, server("lobby")).getResult().isAllowed())
                .isTrue();
        ServerPreConnectEvent first = new ServerPreConnectEvent(steve, server("lobby"), null);
        listener().switched(first);
        assertThat(first.getResult().isAllowed()).isTrue();

        rooms.add("auth");
        assertThat(switching("auth", server("lobby")).getResult().isAllowed()).isTrue();

        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);
        assertThat(switching("survival", server("lobby")).getResult().isAllowed())
                .isTrue();
        verify(steve, never()).sendMessage(any(Component.class));
    }
}
