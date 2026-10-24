package com.uxplima.auth.velocity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import com.uxplima.auth.velocity.adapter.inbound.LockedProxyDoor;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.config.ProxyConfig;
import com.velocitypowered.api.proxy.messages.ChannelRegistrar;
import com.velocitypowered.api.scheduler.Scheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

/**
 * A proxy whose start failed refuses every login, as a server does: with no login running, the network behind it
 * would take anybody under any name.
 */
final class AFailedProxyStartLocksTheDoorTest {

    @Test
    @DisplayName("a value the proxy cannot read registers a door that refuses every login")
    void aBadFileLocksTheProxy(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("config.conf"), "ownership { rule = nowhere }\n", StandardCharsets.UTF_8);
        ProxyServer proxy = mock(ProxyServer.class);
        EventManager events = mock(EventManager.class);
        when(proxy.getEventManager()).thenReturn(events);
        AuthVelocityPlugin plugin = new AuthVelocityPlugin(proxy, mock(Logger.class), folder);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        verify(events).register(eq(plugin), any(LockedProxyDoor.class));
    }

    /** A proxy that a start can be wired on, so the only thing that can lock it is the case under test. */
    private static ProxyServer startable(EventManager events) {
        ProxyServer proxy = mock(ProxyServer.class);
        when(proxy.getEventManager()).thenReturn(events);
        when(proxy.getConfiguration()).thenReturn(mock(ProxyConfig.class));
        when(proxy.getChannelRegistrar()).thenReturn(mock(ChannelRegistrar.class));
        when(proxy.getCommandManager()).thenReturn(mock(CommandManager.class));
        when(proxy.getScheduler()).thenReturn(mock(Scheduler.class, RETURNS_DEEP_STUBS));
        PluginManager plugins = mock(PluginManager.class);
        when(proxy.getPluginManager()).thenReturn(plugins);
        when(plugins.getPlugin("limboapi")).thenReturn(Optional.empty());
        return proxy;
    }

    /** Start once on a fresh folder, so the files are written, then change one line of config.conf. */
    private static void firstStartThen(Path folder, String from, String to) throws IOException {
        new AuthVelocityPlugin(startable(mock(EventManager.class)), mock(Logger.class), folder)
                .onProxyInitialize(new ProxyInitializeEvent());
        Path config = folder.resolve("config.conf");
        String text = Files.readString(config, StandardCharsets.UTF_8);
        assertThat(text).contains(from);
        Files.writeString(config, text.replace(from, to), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("a start that wires leaves the door to the login, with no lock")
    void aGoodStartLocksNothing(@TempDir Path folder) {
        EventManager events = mock(EventManager.class);
        AuthVelocityPlugin plugin = new AuthVelocityPlugin(startable(events), mock(Logger.class), folder);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        verify(events, never()).register(eq(plugin), any(LockedProxyDoor.class));
    }

    @Test
    @DisplayName("a start under another UUID scheme registers a door that refuses every login")
    void anotherSchemeLocksTheProxy(@TempDir Path folder) throws IOException {
        firstStartThen(folder, "uuid-scheme = keep-first", "uuid-scheme = offline");
        EventManager events = mock(EventManager.class);
        AuthVelocityPlugin plugin = new AuthVelocityPlugin(startable(events), mock(Logger.class), folder);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        verify(events).register(eq(plugin), any(LockedProxyDoor.class));
    }

    @Test
    @DisplayName("a proxy asked to hold players in its own world, without LimboAPI, refuses every login")
    void aWaitingWorldItCannotBuildLocksTheProxy(@TempDir Path folder) throws IOException {
        firstStartThen(folder, "wait-in = server", "wait-in = limbo");
        EventManager events = mock(EventManager.class);
        AuthVelocityPlugin plugin = new AuthVelocityPlugin(startable(events), mock(Logger.class), folder);

        plugin.onProxyInitialize(new ProxyInitializeEvent());

        // Players would otherwise wait on a server, and a server with no uxmAuth of its own lets them straight in.
        verify(events).register(eq(plugin), any(LockedProxyDoor.class));
    }

    @Test
    @DisplayName("the locked door denies a login with its reason")
    void theLockedDoorDenies() {
        InboundConnection connection = mock(InboundConnection.class);
        when(connection.getRemoteAddress())
                .thenReturn(new InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 25565));
        PreLoginEvent login = new PreLoginEvent(connection, "Steve", null);

        new LockedProxyDoor().onPreLogin(login);

        assertThat(login.getResult().isAllowed()).isFalse();
        assertThat(login.getResult().getReasonComponent()).isPresent();
    }
}
