package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.api.LoginWay;
import com.uxplima.auth.api.paper.event.LoginEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The server hears a player arrive once they are logged in, and never hears a stranger leave. */
final class JoinMessagesTest {

    private ServerMock server;
    private PlayerMock watcher;
    private final Set<UUID> loggedIn = new HashSet<>();
    private boolean waits = true;
    private JoinMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        watcher = server.addPlayer("Watcher");
        messages = new JoinMessages(() -> waits, loggedIn::contains, watcher);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerJoinEvent joins(PlayerMock player) {
        PlayerJoinEvent event = new PlayerJoinEvent(player, Component.text(player.getName() + " joined"));
        messages.onJoin(event);
        return event;
    }

    @Test
    @DisplayName("the join message is held until the login, and then everybody reads it once")
    void theJoinMessageWaitsForTheLogin() {
        PlayerMock steve = server.addPlayer("Steve");
        PlayerJoinEvent joined = joins(steve);
        assertThat(joined.joinMessage()).isNull();

        loggedIn.add(steve.getUniqueId());
        messages.onLogin(new LoginEvent(steve, LoginWay.PASSWORD));
        messages.onLogin(new LoginEvent(steve, LoginWay.PASSWORD));

        assertThat(watcher.nextComponentMessage()).isEqualTo(Component.text("Steve joined"));
        assertThat(watcher.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a player who leaves before logging in leaves no quit message, and no join message after")
    void aStrangerLeavesNoTrace() {
        PlayerMock steve = server.addPlayer("Steve");
        joins(steve);
        PlayerQuitEvent quit =
                new PlayerQuitEvent(steve, Component.text("Steve left"), PlayerQuitEvent.QuitReason.DISCONNECTED);

        messages.onQuit(quit);

        assertThat(quit.quitMessage()).isNull();
        messages.onLogin(new LoginEvent(steve, LoginWay.PASSWORD));
        assertThat(watcher.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a player logged in at the join, by Mojang or a session, and a server that does not wait, keep both")
    void otherwiseBothStay() {
        PlayerMock notch = server.addPlayer("Notch");
        loggedIn.add(notch.getUniqueId());
        assertThat(joins(notch).joinMessage()).isEqualTo(Component.text("Notch joined"));
        PlayerQuitEvent quit =
                new PlayerQuitEvent(notch, Component.text("Notch left"), PlayerQuitEvent.QuitReason.DISCONNECTED);
        messages.onQuit(quit);
        assertThat(quit.quitMessage()).isEqualTo(Component.text("Notch left"));

        waits = false;
        PlayerMock steve = server.addPlayer("Steve");
        assertThat(joins(steve).joinMessage()).isEqualTo(Component.text("Steve joined"));
    }
}
