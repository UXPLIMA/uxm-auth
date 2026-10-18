package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** {@code /session on|off}: a logged-in player chooses whether this address skips the password next time. */
final class SessionCommandTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private SessionCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        command = new SessionCommand(
                world.remember,
                world.remembering,
                world.sessions,
                () -> world.sessionSettings,
                world.scheduler,
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
                        .run(steve.getUniqueId(), Password.of("hunter22"), address())
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    private UUID account() {
        return steve.getUniqueId();
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
    }

    @Test
    @DisplayName("on stores the choice and remembers this login from this address for the time")
    void onRemembersThisLogin() {
        loggedIn();

        command.attempt(steve, true);
        world.scheduler.drain();

        assertThat(world.remember.chose(account())).isTrue();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isTrue();
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.SESSION_ON, Text.placeholder("minutes", "60")));
    }

    @Test
    @DisplayName("off stores the choice and ends every remembered session of the account")
    void offEndsTheSessions() {
        loggedIn();
        command.attempt(steve, true);
        world.scheduler.drain();
        steve.nextComponentMessage();

        command.attempt(steve, false);
        world.scheduler.drain();

        assertThat(world.remember.chose(account())).isFalse();
        assertThat(world.remembered.holds(account(), address(), world.clock.instant()))
                .isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.SESSION_OFF));
    }

    @Test
    @DisplayName("a player not logged in is told to log in first, and nothing is stored")
    void aPlayerNotLoggedInIsRefused() {
        world.registered(steve, "hunter22");

        command.attempt(steve, true);
        world.scheduler.drain();

        assertThat(world.remember.chose(account())).isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.SESSION_LOG_IN_FIRST));
    }

    @Test
    @DisplayName("while sessions are not by choice the command says so, and nothing is stored")
    void nothingToChooseWhileSessionsAreNotChosen() {
        loggedIn();
        world.sessionSettings = new Remember.Settings(Remember.Mode.OFF, Duration.ofMinutes(60));

        command.attempt(steve, true);
        world.scheduler.drain();

        assertThat(world.remember.chose(account())).isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.SESSION_NOT_CHOSEN));
    }
}
