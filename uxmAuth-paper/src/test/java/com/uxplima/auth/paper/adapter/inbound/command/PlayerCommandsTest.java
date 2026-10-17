package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.bukkit.potion.PotionEffectType;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
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

/** The four commands a player types to get in, from the typed line to what the player sees. */
final class PlayerCommandsTest {

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
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the right password lets the player in, gives back what the hold took and runs the login moment")
    void loginLetsThePlayerIn() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isFalse();
    }

    @Test
    @DisplayName("a wrong password says how many tries are left, and the last one kicks")
    void aWrongPasswordCountsDownToAKick() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();
        drain();

        world.scheduler.settle(login().attempt(steve, "wrong123"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.LOGIN_WRONG, Text.placeholder("left", "2")));
        assertThat(world.fired).contains(AuthSettings.Moment.WRONG_PASSWORD);

        world.scheduler.settle(login().attempt(steve, "wrong123"));
        world.scheduler.settle(login().attempt(steve, "wrong123"));

        assertThat(world.fired).contains(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
        assertThat(steve.isOnline()).isFalse();
    }

    @Test
    @DisplayName("a registration with the password twice makes the account and lets the player in")
    void registerMakesTheAccount() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.accounts.findByName(new PlayerName("steve"))).isPresent();
        assertThat(world.fired).contains(AuthSettings.Moment.REGISTERED);
    }

    @Test
    @DisplayName("while the confirmation is asked for, one password is not enough")
    void registerAsksForTheConfirmation() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", ""));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.REGISTER_CONFIRM_NEEDED));
        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("with the confirmation off, one password makes the account")
    void registerWithoutTheConfirmation() {
        world.unregistered(steve);
        AuthSettings.Passwords once = new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), false, true, 3);

        world.scheduler.settle(register(once).attempt(steve, "hunter22", ""));

        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("a refused password is told with its reason")
    void aRefusedPasswordIsExplained() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "abc", "abc"));

        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.PASSWORD_TOO_SHORT, Text.placeholder("min", "6")));
    }

    @Test
    @DisplayName("a change needs the old password while old-on-change is on")
    void changeNeedsBothWhileAsked() {
        loggedIn("hunter22");

        world.scheduler.settle(changePassword(AuthWorld.shippedPasswords()).attempt(steve, "fresh777", ""));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CHANGE_NEEDS_BOTH));

        world.scheduler.settle(changePassword(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "fresh777"));
        assertThat(world.fired).contains(AuthSettings.Moment.PASSWORD_CHANGED);
    }

    @Test
    @DisplayName("a logout puts the player back at the door")
    void logoutHoldsThePlayerAgain() {
        loggedIn("hunter22");

        logout().attempt(steve);
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_OUT, AuthSettings.Moment.JOIN_REGISTERED);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    @Test
    @DisplayName("a logout of a player who is not logged in says so")
    void logoutWithoutALogin() {
        world.registered(steve, "hunter22");

        logout().attempt(steve);

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGOUT_NOT_LOGGED_IN));
    }

    @Test
    @DisplayName("a right password typed after another connection opened the session again opens nothing")
    void aLoginForAReplacedOpeningOpensNothing() {
        world.registered(steve, "hunter22");
        world.bindOpening(steve);
        world.sessions.open(
                steve.getUniqueId(), world.sessions.state(steve.getUniqueId()).orElseThrow());

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a registration typed after another connection opened the session again makes nothing")
    void aRegistrationForAReplacedOpeningMakesNothing() {
        world.unregistered(steve);
        world.bindOpening(steve);
        world.sessions.open(
                steve.getUniqueId(), world.sessions.state(steve.getUniqueId()).orElseThrow());

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    private void loggedIn(String password) {
        world.registered(steve, password);
        world.scheduler.settle(login().attempt(steve, password));
        assertThat(world.isAuthenticated(steve)).isTrue();
        drain();
    }

    private void drain() {
        while (steve.nextComponentMessage() != null) {
            // Throw away what came before the part under test.
        }
    }

    private LoginCommand login() {
        return new LoginCommand(
                world.logIn, world.door, world::openingOf, world.moments, world.messages, world.replies);
    }

    private RegisterCommand register(AuthSettings.Passwords passwords) {
        return new RegisterCommand(
                world.register, world.door, world::openingOf, () -> passwords, world.messages, world.replies);
    }

    private ChangePasswordCommand changePassword(AuthSettings.Passwords passwords) {
        return new ChangePasswordCommand(world.change, world.moments, () -> passwords, world.messages, world.replies);
    }

    private LogoutCommand logout() {
        return new LogoutCommand(world.logOut, world.door, world.moments, world.messages);
    }
}
