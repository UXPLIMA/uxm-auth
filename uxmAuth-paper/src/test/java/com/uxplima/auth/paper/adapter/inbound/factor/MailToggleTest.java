package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
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

/** {@code /2fa email on|off}, and the login it changes: a code mailed as the step opens, typed with /2fa. */
final class MailToggleTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private MailToggle toggle;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        toggle = world.mailToggle();
        world.registered(steve, "hunter22");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UUID account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
    }

    private LoginResult logIn() {
        return world.logIn
                .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                .join();
    }

    @Test
    @DisplayName("on needs a linked address and says where codes go, masked; with none it says to link one")
    void onNeedsAnAddress() {
        logIn();

        world.scheduler.settle(toggle.on(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_MAIL_NO_ADDRESS));
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());
        world.scheduler.settle(toggle.on(steve));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve, Keys.FACTOR_MAIL_ON, Text.placeholder("address", "b***@e***.com")));
        world.scheduler.settle(toggle.on(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_MAIL_ALREADY_ON));
    }

    @Test
    @DisplayName("with it on, a right password mails the code and asks it, and the mailed code logs in once")
    void theMailedCodeLogsIn() {
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());
        world.emails.setMailsLoginCode(account(), true);

        assertThat(logIn()).isEqualTo(new LoginResult.CodeNeeded());
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (world.mailed.isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        String code = Objects.requireNonNull(world.mailed.getLast().get("code"), "code");

        assertThat(world.confirmFactor.confirm(steve.getUniqueId(), code, "127.0.0.1"))
                .isInstanceOf(com.uxplima.auth.application.factor.ConfirmFactor.Result.LoggedIn.class);
    }

    @Test
    @DisplayName("off takes a code that passes; a wrong one is said and keeps it on")
    void offNeedsACode() {
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());
        world.emails.setMailsLoginCode(account(), true);
        com.uxplima.auth.domain.factor.TotpSecret secret = world.appOn(steve);
        world.sessions.update(
                steve.getUniqueId(),
                state -> new com.uxplima.auth.domain.state.AuthState.Authenticated(
                        world.accounts.findByName(new PlayerName("Steve")).orElseThrow(),
                        com.uxplima.auth.domain.state.AuthMethod.PASSWORD));

        world.scheduler.settle(toggle.off(steve, "000000"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_WRONG));
        assertThat(world.emails.mailsLoginCode(account())).isTrue();
        world.scheduler.settle(toggle.off(steve, world.codeNow(secret)));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_MAIL_OFF));
        assertThat(world.emails.mailsLoginCode(account())).isFalse();
    }

    @Test
    @DisplayName("a player not logged in is told to log in first")
    void notLoggedIn() {
        world.scheduler.settle(toggle.on(steve));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_LOG_IN_FIRST));
    }
}
