package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.potion.PotionEffectType;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Bulletin;
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

/** A player of an account whose sessions ended is back at the door, unless they made the change. */
final class EndedSessionsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private EndedSessions ended;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        ended = new EndedSessions(
                server::getPlayer,
                world.sessions,
                world.scheduler,
                world.logOut,
                world.door,
                world.moments,
                world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void loggedIn() {
        world.registered(steve, "hunter22");
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        while (steve.nextComponentMessage() != null) {
            // Throw away what came before the part under test.
        }
    }

    @Test
    @DisplayName("a logged-in player whose sessions ended reads why and is held at the door again")
    void aLoginEnds() {
        loggedIn();

        ended.accept(new Bulletin.SessionsEnded(steve.getUniqueId(), Optional.empty()));
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGOUT_SESSIONS_ENDED));
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_OUT);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    @Test
    @DisplayName("the session that made the change, one already waiting, and one not here are left alone")
    void othersAreLeftAlone() {
        loggedIn();
        String visit = world.sessions.held(steve.getUniqueId()).orElseThrow().visit();

        ended.accept(new Bulletin.SessionsEnded(steve.getUniqueId(), Optional.of(visit)));
        ended.accept(new Bulletin.SessionsEnded(UUID.randomUUID(), Optional.empty()));
        world.scheduler.drain();
        assertThat(world.isAuthenticated(steve)).isTrue();

        world.logOut.run(steve.getUniqueId());
        ended.accept(new Bulletin.SessionsEnded(steve.getUniqueId(), Optional.empty()));
        world.scheduler.drain();
        assertThat(steve.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a second session of the account here ends when the change was made in another one")
    void aSecondSessionEnds() {
        loggedIn();

        ended.accept(new Bulletin.SessionsEnded(steve.getUniqueId(), Optional.of("the-other-visit")));
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isFalse();
    }
}
