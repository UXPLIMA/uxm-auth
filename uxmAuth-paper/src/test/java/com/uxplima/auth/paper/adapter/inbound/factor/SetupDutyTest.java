package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.HoldListener;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A player holding the node the operator names plays only once the app is on, at every join, and only while held. */
final class SetupDutyTest {

    private static final String NODE = "uxmauth.2fa.required";

    @TempDir
    Path folder;

    private ServerMock server;
    private Plugin plugin;
    private AuthWorld world;
    private PlayerMock steve;
    private FactorSetup setup;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = new AuthWorld(plugin, folder, AuthWorld.shippedPasswords());
        world.requiredNode = NODE;
        steve = server.addPlayer("Steve");
        setup = world.factorSetup();
        world.registered(steve, "hunter22");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void loggedIn() {
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
    }

    private AuthState state() {
        return world.sessions.state(steve.getUniqueId()).orElseThrow();
    }

    private Account account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow();
    }

    /** Runs the player's thread until the condition holds; at most ten seconds. */
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            assertThat(System.nanoTime()).as("within ten seconds").isLessThan(deadline);
            world.scheduler.drain();
            Thread.onSpinWait();
        }
    }

    private List<Component> said() {
        List<Component> said = new ArrayList<>();
        Component next;
        while ((next = steve.nextComponentMessage()) != null) {
            said.add(next);
        }
        return said;
    }

    private String turnedOn() {
        world.scheduler.settle(setup.setup(steve));
        String key = copied(steve.nextComponentMessage());
        said();
        world.scheduler.settle(setup.confirm(steve, world.codeNow(TotpSecret.fromBase32(key))));
        return key;
    }

    private static String copied(Component message) {
        ClickEvent<?> click = message.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            return ((ClickEvent.Payload.Text) click.payload()).value();
        }
        return message.children().stream()
                .map(SetupDutyTest::copied)
                .filter(text -> !text.isEmpty())
                .findFirst()
                .orElse("");
    }

    @Test
    @DisplayName("a player with the node and no app is held at setup, told so, and let in once the app is on")
    void heldUntilTheAppIsOn() {
        steve.addAttachment(plugin, NODE, true);

        loggedIn();

        assertThat(state()).isEqualTo(new AuthState.SettingUp(account(), AuthMethod.PASSWORD));
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);
        await(() -> !said().isEmpty());
        turnedOn();
        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
        assertThat(world.fired).containsOnlyOnce(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("the hold says why, and a player held for the app may still do nothing but set it up")
    void theHoldSaysWhy() {
        steve.addAttachment(plugin, NODE, true);

        loggedIn();
        List<Component> told = new ArrayList<>();
        await(() -> told.addAll(said()) && !told.isEmpty());

        assertThat(told).containsExactly(world.messages.render(steve, Keys.FACTOR_REQUIRED));
        assertThat(new HoldListener(world.sessions, () -> AuthWorld.hold(true), () -> Set.of("2fa"), "uxmauth")
                        .isHeld(steve))
                .isTrue();
    }

    @Test
    @DisplayName("a player who leaves mid-setup is held again at the next join")
    void leavingMidSetupHoldsAgain() {
        steve.addAttachment(plugin, NODE, true);
        loggedIn();
        world.scheduler.settle(setup.setup(steve));
        setup.left(steve);
        world.sessions.open(steve.getUniqueId(), new AuthState.Registered(account(), Attempts.fresh(3)));

        loggedIn();

        assertThat(state()).isInstanceOf(AuthState.SettingUp.class);
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a player whose node was taken away is no longer asked")
    void noNodeNoHold() {
        steve.addAttachment(plugin, NODE, false);

        loggedIn();

        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("an operator is asked only when granted the node outright, not by the server's default for operators")
    void anOperatorIsNotAskedByDefault() {
        steve.setOp(true);

        loggedIn();

        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("an operator who names no node asks nobody, not even an operator the server grants every node")
    void noNodeNamed() {
        world.requiredNode = "";
        steve.addAttachment(plugin, NODE, true);
        steve.setOp(true);

        loggedIn();

        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("a player with the node whose app is on already is let in once the table says so")
    void anAppAlreadyOnLetsIn() {
        steve.addAttachment(plugin, NODE, true);
        world.appOn(steve);
        world.sessions.update(steve.getUniqueId(), state -> new AuthState.Authenticated(account(), AuthMethod.PREMIUM));

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);
        await(() -> state() instanceof AuthState.Authenticated);

        assertThat(world.fired).containsExactly(AuthSettings.Moment.LOGGED_IN);
        assertThat(said()).doesNotContain(world.messages.render(steve, Keys.FACTOR_REQUIRED));
    }

    @Test
    @DisplayName("a player who arrives logged in, by a session or a premium name, is held the same way")
    void anArrivalLoggedInIsHeld() {
        steve.addAttachment(plugin, NODE, true);
        world.sessions.update(steve.getUniqueId(), state -> new AuthState.Authenticated(account(), AuthMethod.SESSION));

        world.door.hold(steve);

        assertThat(state()).isEqualTo(new AuthState.SettingUp(account(), AuthMethod.SESSION));
        await(() -> !said().isEmpty());
        turnedOn();
        assertThat(state()).isEqualTo(new AuthState.Authenticated(account(), AuthMethod.SESSION));
        assertThat(world.reported).contains("in:SESSION");
    }
}
