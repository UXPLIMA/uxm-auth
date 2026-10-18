package com.uxplima.auth.paper.adapter.inbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Where the operator requires an address, a password account plays only once it has a confirmed one. */
final class AddressDutyTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private Plugin plugin;
    private AuthWorld world;
    private PlayerMock steve;
    private EmailLink link;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = new AuthWorld(plugin, folder, AuthWorld.shippedPasswords());
        world.emailRequired = true;
        steve = server.addPlayer("Steve");
        link = world.emailLink(EmailLink.Forms.NONE, id -> false);
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

    private void confirmed(String address) {
        world.scheduler.settle(link.add(steve, address));
        String code = Objects.requireNonNull(world.mailed.getLast().get("code"), "code");
        world.scheduler.settle(link.confirm(steve, code));
    }

    @Test
    @DisplayName("an account with no address is held after the login, told why, and let in once one is confirmed")
    void heldUntilAnAddressIsConfirmed() {
        loggedIn();

        assertThat(state()).isEqualTo(new AuthState.SettingUp(account(), AuthMethod.PASSWORD));
        List<Component> told = new ArrayList<>();
        await(() -> told.addAll(said()) && told.contains(world.messages.render(steve, Keys.EMAIL_REQUIRED)));
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);

        confirmed("steve@example.net");

        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
        assertThat(world.fired).containsOnlyOnce(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("an account with a confirmed address is let in")
    void anAddressLetsIn() {
        world.emails.setAddress(
                account().id(), EmailAddress.parse("steve@example.net").orElseThrow());

        loggedIn();

        await(() -> state() instanceof AuthState.Authenticated);
        assertThat(world.fired).containsOnlyOnce(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("nobody is held where no address is required, nor where mail is off and nobody could confirm one")
    void heldOnlyWhenItCanBeDone() {
        world.emailRequired = false;
        loggedIn();
        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);

        world.emailRequired = true;
        world.mailEnabled = false;
        assertThat(world.addressDuty().owes(steve)).isFalse();
    }

    @Test
    @DisplayName("an account without a password, premium or Bedrock, is never held for an address")
    void onlyPasswordAccounts() {
        AuthState.Authenticated premium = new AuthState.Authenticated(
                new Account(
                        account().id(),
                        account().name(),
                        AccountKind.PREMIUM,
                        null,
                        null,
                        account().registeredAt(),
                        "127.0.0.1",
                        null,
                        null),
                AuthMethod.PREMIUM);
        AtomicInteger proceeded = new AtomicInteger();

        world.addressDuty().hold(steve, premium, proceeded::incrementAndGet);

        assertThat(proceeded).hasValue(1);
        assertThat(state()).isNotInstanceOf(AuthState.SettingUp.class);
    }

    @Test
    @DisplayName("a player who leaves while held is forgotten, and owes the address again at the next login")
    void leavingForgetsTheHold() {
        loggedIn();
        assertThat(state()).isInstanceOf(AuthState.SettingUp.class);

        world.addressDuty()
                .onQuit(new PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(world.addressOwed.release(steve)).isFalse();
    }

    @Test
    @DisplayName("where an address is required, /email remove keeps it and says why")
    void aRequiredAddressIsKept() {
        world.emails.setAddress(
                account().id(), EmailAddress.parse("steve@example.net").orElseThrow());
        loggedIn();
        await(() -> state() instanceof AuthState.Authenticated);
        said();

        world.scheduler.settle(link.remove(steve));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.EMAIL_REQUIRED_KEEP));
        assertThat(world.emails.address(account().id())).isPresent();
    }

    @Test
    @DisplayName("a player who owes the app and an address sets the app up first, then links the address, then plays")
    void theAppThenTheAddress() {
        world.requiredNode = "uxmauth.staff";
        steve.addAttachment(plugin, "uxmauth.staff", true);
        FactorSetup setup = world.factorSetup();
        loggedIn();
        await(() -> !said().isEmpty());

        world.scheduler.settle(setup.setup(steve));
        String key = copied(Objects.requireNonNull(steve.nextComponentMessage()));
        said();
        world.scheduler.settle(setup.confirm(steve, world.codeNow(TotpSecret.fromBase32(key))));
        assertThat(state()).as("the address is still owed").isInstanceOf(AuthState.SettingUp.class);
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);

        confirmed("steve@example.net");

        assertThat(state()).isInstanceOf(AuthState.Authenticated.class);
        assertThat(world.fired).containsOnlyOnce(AuthSettings.Moment.LOGGED_IN);
    }

    private static String copied(Component message) {
        ClickEvent<?> click = message.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            return ((ClickEvent.Payload.Text) click.payload()).value();
        }
        return message.children().stream()
                .map(AddressDutyTest::copied)
                .filter(text -> !text.isEmpty())
                .findFirst()
                .orElse("");
    }
}
