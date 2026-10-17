package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A password login is remembered from the player's address; a login the door did not take is not. */
final class RememberingTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        world.sessionSettings = new Remember.Settings(Remember.Mode.EVERYONE, java.time.Duration.ofMinutes(60));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UUID account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
    }

    @Test
    @DisplayName("a password login in the world is remembered from the player's address")
    void aLoginInTheWorldIsRemembered() {
        world.registered(steve, "hunter22");
        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        world.scheduler.drain();

        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isTrue();
    }

    @Test
    @DisplayName(
            "a login before the world is remembered at the join, and a session that let them in is not written again")
    void aLoginBeforeTheWorldIsRemembered() {
        world.registered(steve, "hunter22");
        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();

        world.door.hold(steve);
        world.scheduler.drain();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isTrue();

        world.remembered.forget(account());
        world.fired.clear();
        world.sessions.update(
                steve.getUniqueId(),
                state -> new com.uxplima.auth.domain.state.AuthState.Authenticated(
                        ((com.uxplima.auth.domain.state.AuthState.Authenticated) state).account(),
                        com.uxplima.auth.domain.state.AuthMethod.SESSION));
        world.door.hold(steve);
        world.scheduler.drain();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a login through a shared-address host is not remembered")
    void aSharedHostIsNotRemembered() {
        world.sharedHosts = java.util.List.of("*.tcpshield.com");
        world.registered(steve, "hunter22");
        world.sessions.open(
                steve.getUniqueId(),
                world.sessions.state(steve.getUniqueId()).orElseThrow(),
                "v1",
                "mc.tcpshield.com:25565");
        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        world.scheduler.drain();

        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();
    }
}
