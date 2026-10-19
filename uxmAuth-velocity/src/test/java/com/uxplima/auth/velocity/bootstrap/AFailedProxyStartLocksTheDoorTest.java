package com.uxplima.auth.velocity.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.auth.velocity.adapter.inbound.LockedProxyDoor;
import com.velocitypowered.api.event.EventManager;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.ProxyServer;
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
