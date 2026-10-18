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
        world.sharedSources = java.util.List.of(address());
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

    @Test
    @DisplayName("a login the player left before it was written down is not remembered, nor one whose password changed")
    void aLoginThatEndedIsNotRemembered() {
        com.uxplima.uxmlib.scheduler.Scheduler late =
                org.mockito.Mockito.mock(com.uxplima.uxmlib.scheduler.Scheduler.class);
        Remembering remembering = new Remembering(world.remember, world.sessions, late);
        world.registered(steve, "hunter22");
        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();

        remembering.loggedIn(steve, authenticated());
        world.logOut.run(steve.getUniqueId());
        lastWrite(late).run();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();

        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();
        remembering.loggedIn(steve, authenticated());
        world.change
                .run(steve.getUniqueId(), Password.of("hunter22"), Password.of("fresh777"))
                .join();
        lastWrite(late).run();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();
    }

    @Test
    @DisplayName(
            "while sessions are by choice, a login whose screen ticked the box stores the choice and is remembered")
    void aLoginThatChoseIsRemembered() {
        world.sessionSettings = new Remember.Settings(Remember.Mode.CHOSEN, java.time.Duration.ofMinutes(60));
        world.registered(steve, "hunter22");
        world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), address()).join();

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        world.scheduler.drain();
        assertThat(world.remember.chose(account())).isFalse();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();

        world.sessions.chose(steve.getUniqueId(), true);
        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        world.scheduler.drain();
        assertThat(world.remember.chose(account())).isTrue();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isTrue();
    }

    private com.uxplima.auth.domain.state.AuthState.Authenticated authenticated() {
        return (com.uxplima.auth.domain.state.AuthState.Authenticated)
                world.sessions.state(steve.getUniqueId()).orElseThrow();
    }

    /** The write the scheduler was last handed, which a real one would run a moment later. */
    private static Runnable lastWrite(com.uxplima.uxmlib.scheduler.Scheduler late) {
        org.mockito.ArgumentCaptor<Runnable> write = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        org.mockito.Mockito.verify(late, org.mockito.Mockito.atLeastOnce()).async(write.capture());
        return write.getValue();
    }
}
