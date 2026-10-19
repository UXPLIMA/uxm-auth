package com.uxplima.auth.paper.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/** While the console moves the accounts, nobody comes in to write what the copy would miss. */
final class ClosedDoorTest {

    @TempDir
    Path folder;

    private AuthWorld world;
    private final AtomicBoolean moving = new AtomicBoolean();
    private ClosedDoor door;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        door = new ClosedDoor(moving::get, world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @SuppressWarnings("removal")
    private static AsyncPlayerPreLoginEvent arriving() {
        return new AsyncPlayerPreLoginEvent("Steve", InetAddress.getLoopbackAddress(), UUID.randomUUID());
    }

    @Test
    @DisplayName("while a move runs a connection is turned away with the reason; otherwise it is left alone")
    void theDoorIsShutWhileAMoveRuns() {
        AsyncPlayerPreLoginEvent before = arriving();
        door.onPreLogin(before);
        assertThat(before.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);

        moving.set(true);
        AsyncPlayerPreLoginEvent during = arriving();
        door.onPreLogin(during);

        assertThat(during.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.KICK_OTHER);
        assertThat(during.kickMessage()).isEqualTo(world.messages.render(Audience.empty(), Keys.DATABASE_DOOR_SHUT));
    }
}
