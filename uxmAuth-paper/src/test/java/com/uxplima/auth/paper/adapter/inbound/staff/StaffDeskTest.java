package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.PasswordWords;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.application.staff.FactorOff;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.application.staff.ForceRegister;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.application.staff.SetPassword;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.EndedSessions;
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

/** Staff acting on an account: a gate in game, a forced login over the bulletins, and an unban. */
final class StaffDeskTest {

    private static final String HOME = "203.0.113.7";
    private static final java.util.UUID MOJANG = java.util.UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private final Set<StaffAction> opened = EnumSet.noneOf(StaffAction.class);
    private final List<String> logged = new ArrayList<>();
    private StaffGate gate;
    private StaffDesk desk;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
        LocalEvents events = new LocalEvents();
        ForceLogin force = new ForceLogin(world.accounts, world.sessions, events, world.clock);
        events.listen(
                new ForcedLogins(server::getPlayer, force, world.lane, world.replies, world.door, world.messages));
        gate = new StaffGate(action -> !action.destructive() || opened.contains(action), world.messages);
        events.listen(new RemovedAccounts(server::getPlayer, world.scheduler, world.messages));
        events.listen(new EndedSessions(
                server::getPlayer,
                world.sessions,
                world.scheduler,
                world.logOut,
                world.door,
                world.moments,
                world.messages));
        StaffDesk.Cases cases = new StaffDesk.Cases(
                force,
                world.bans,
                new Unregister(world.accounts, events),
                new SetPassword(
                        world.accounts,
                        world.hashing,
                        () -> world.policy,
                        BreachedPasswords.NONE,
                        world.remembered,
                        events),
                new ForceRegister(
                        world.accounts,
                        world.hashing,
                        AuthWorld::shippedNames,
                        () -> world.policy,
                        BreachedPasswords.NONE,
                        world.clock),
                new FactorOff(world.accounts, world.factors, world.emails),
                new MarkKind(
                        world.accounts,
                        name -> new PremiumAnswer.Premium(MOJANG, name.value()),
                        UuidScheme.KEEP_FIRST,
                        events));
        StaffDesk.Reach reach = new StaffDesk.Reach(
                name -> java.util.Optional.ofNullable(server.getPlayerExact(name))
                        .map(org.bukkit.entity.Player::getUniqueId),
                new WaitingAccounts(
                        server::getPlayerExact,
                        world.sessions,
                        () -> AuthWorld.MAX_ATTEMPTS,
                        world.door,
                        world.scheduler,
                        world.messages));
        desk = new StaffDesk(gate, cases, reach, world.lane, world.replies, world.messages, logged::add);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("in game a destructive command is refused unless staff.in-game opens it; the console always runs it")
    void theGateHoldsDestructiveCommands() {
        assertThat(gate.allows(staff, StaffAction.UNREGISTER)).isFalse();
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "unregister")));
        assertThat(gate.allows(staff, StaffAction.UNBAN)).isTrue();
        assertThat(gate.allows(server.getConsoleSender(), StaffAction.UNREGISTER))
                .isTrue();

        opened.add(StaffAction.UNREGISTER);
        assertThat(gate.allows(staff, StaffAction.UNREGISTER)).isTrue();
        assertThat(staff.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName(
            "a forced login is refused in game unless opened; opened, it reaches the waiting player and lets them in")
    void aForcedLoginLetsThePlayerIn() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.scheduler.settle(desk.forceLogin(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .as("a forced login hands the account to whoever waits under the name: console only by default")
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "forcelogin")));
        opened.add(StaffAction.FORCE_LOGIN);

        world.scheduler.settle(desk.forceLogin(staff, "Steve"));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !world.fired.contains(AuthSettings.Moment.LOGGED_IN)) {
            world.scheduler.drain();
            Thread.onSpinWait();
        }
        world.scheduler.drain();

        assertThat(world.fired).as("the door opened as for any login").contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(world.sessions.state(steve.getUniqueId()).orElseThrow())
                .isInstanceOfSatisfying(
                        AuthState.Authenticated.class,
                        in -> assertThat(in.method()).isEqualTo(AuthMethod.FORCED));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_FORCED_LOGIN, Text.placeholder("name", "Steve")));
        assertThat(logged).singleElement().asString().contains("Staff").contains("Steve");
        List<Object> told = new ArrayList<>();
        Object next;
        while ((next = steve.nextComponentMessage()) != null) {
            told.add(next);
        }
        assertThat(told).contains(world.messages.render(steve, Keys.STAFF_LET_IN));
    }

    @Test
    @DisplayName("a forced login of a name with no account says so and asks nobody")
    void anUnknownNameIsSaid() {
        opened.add(StaffAction.FORCE_LOGIN);
        world.scheduler.settle(desk.forceLogin(staff, "Nobody"));

        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_UNKNOWN, Text.placeholder("name", "Nobody")));
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("an unban lifts the ban; an address never banned, or no address, says so")
    void anUnbanLiftsTheBan() {
        for (int i = 0;
                i < 20
                        && world.bans
                                .rule()
                                .check(new Admission.Arrival(HOME, ""))
                                .isEmpty();
                i++) {
            world.bans.failed(HOME, "play.example.org");
        }

        assertThat(world.bans.rule().check(new Admission.Arrival(HOME, ""))).isPresent();

        world.scheduler.settle(desk.unban(staff, HOME));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_UNBANNED, Text.placeholder("address", HOME)));
        assertThat(world.bans.rule().check(new Admission.Arrival(HOME, ""))).isEmpty();
        assertThat(logged).singleElement().asString().contains(HOME);

        world.scheduler.settle(desk.unban(staff, HOME));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_NOT_BANNED, Text.placeholder("address", HOME)));
        world.scheduler.settle(desk.unban(staff, "nowhere"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_NOT_AN_ADDRESS, Text.placeholder("address", "nowhere")));
    }

    private void until(java.util.function.BooleanSupplier done) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline && !done.getAsBoolean()) {
            world.scheduler.drain();
            Thread.onSpinWait();
        }
        world.scheduler.drain();
    }

    @Test
    @DisplayName("in game an unregistration is refused unless opened; from the console the account goes and its"
            + " player is sent away")
    void anUnregistrationRemovesTheAccount() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");

        world.scheduler.settle(desk.unregister(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "unregister")));
        assertThat(world.accounts.findByName(new PlayerName("Steve"))).isPresent();

        world.scheduler.settle(desk.unregister(server.getConsoleSender(), "Steve"));
        until(() -> !steve.isOnline());

        assertThat(world.accounts.findByName(new PlayerName("Steve"))).isEmpty();
        assertThat(steve.isOnline()).isFalse();
        assertThat(logged).singleElement().asString().contains("Steve");
    }

    @Test
    @DisplayName("a set password works at the next login, ends the account's sessions, and stays out of the console")
    void aSetPasswordEndsTheSessions() {
        opened.add(StaffAction.SET_PASSWORD);
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.logIn
                .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                .join();

        world.scheduler.settle(desk.setPassword(staff, "Steve", "fresh7777"));
        until(() -> !(world.sessions.state(steve.getUniqueId()).orElseThrow() instanceof AuthState.Authenticated));

        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_PASSWORD_SET, Text.placeholder("name", "Steve")));
        assertThat(world.sessions.state(steve.getUniqueId()).orElseThrow()).isInstanceOf(AuthState.Registered.class);
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("fresh7777"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(logged).singleElement().asString().contains("Steve").doesNotContain("fresh7777");
    }

    @Test
    @DisplayName("a password the rules refuse is answered with the reason, and an unknown name says so")
    void aRefusedPasswordSaysWhy() {
        opened.add(StaffAction.SET_PASSWORD);
        world.registered(server.addPlayer("Steve"), "hunter22");

        world.scheduler.settle(desk.setPassword(staff, "Steve", "abc"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(PasswordWords.reason(world.messages, staff, world.policy.check(Password.of("abc")))
                        .orElseThrow());
        world.scheduler.settle(desk.setPassword(staff, "Nobody", "fresh7777"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_UNKNOWN, Text.placeholder("name", "Nobody")));
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("staff make an account for a waiting player: it takes their id, and they log in with its password")
    void aForcedRegistrationLetsThePlayerLogIn() {
        PlayerMock steve = server.addPlayer("Steve");
        world.sessions.open(steve.getUniqueId(), new AuthState.Unregistered(new PlayerName("Steve"), false));

        world.scheduler.settle(desk.forceRegister(staff, "Steve", "hunter22"));
        until(() -> world.sessions.state(steve.getUniqueId()).orElseThrow() instanceof AuthState.Registered);

        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_REGISTERED, Text.placeholder("name", "Steve")));
        assertThat(world.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .id())
                .isEqualTo(steve.getUniqueId());
        List<Object> told = new ArrayList<>();
        Object next;
        while ((next = steve.nextComponentMessage()) != null) {
            told.add(next);
        }
        assertThat(told).contains(world.messages.render(steve, Keys.STAFF_REGISTERED_YOU));
        assertThat(world.fired).as("the door greets them to log in now").contains(AuthSettings.Moment.JOIN_REGISTERED);
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(logged).singleElement().asString().contains("Steve").doesNotContain("hunter22");
    }

    @Test
    @DisplayName("a held name and a name the rules refuse make no account, and say why")
    void aForcedRegistrationRefuses() {
        world.registered(server.addPlayer("Steve"), "hunter22");

        world.scheduler.settle(desk.forceRegister(staff, "steve", "hunter22"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_NAME_TAKEN, Text.placeholder("name", "steve")));
        world.scheduler.settle(desk.forceRegister(staff, "no way", "hunter22"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_NAME_REFUSED, Text.placeholder("name", "no way")));
        assertThat(logged).isEmpty();
    }

    @Test
    @DisplayName("turning a second factor off is refused in game unless opened; from the console the app goes")
    void aFactorIsTurnedOff() {
        world.registered(server.addPlayer("Steve"), "hunter22");
        java.util.UUID steve =
                world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
        world.factors.setApp(steve, new com.uxplima.auth.domain.factor.TotpSecret(new byte[20]));

        world.scheduler.settle(desk.disableFactor(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "disable2fa")));
        assertThat(world.factors.app(steve)).isPresent();

        opened.add(StaffAction.DISABLE_2FA);
        world.scheduler.settle(desk.disableFactor(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_FACTOR_OFF, Text.placeholder("name", "Steve")));
        assertThat(world.factors.app(steve)).isEmpty();
        world.scheduler.settle(desk.disableFactor(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_FACTOR_NONE, Text.placeholder("name", "Steve")));
        assertThat(logged).singleElement().asString().contains("Steve");
    }

    @Test
    @DisplayName("marking premium is refused in game unless opened; opened, the player rejoins under the handshake")
    void aMarkedPremiumRejoins() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        world.scheduler.settle(desk.markPremium(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .as("marking premium hands the account to the paid owner: console only by default")
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "markpremium")));
        opened.add(StaffAction.MARK_PREMIUM);

        world.scheduler.settle(desk.markPremium(staff, "Steve"));
        until(() -> !steve.isOnline());

        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_MARKED_PREMIUM, Text.placeholder("name", "Steve")));
        assertThat(world.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .premiumId())
                .isEqualTo(MOJANG);
        assertThat(steve.isOnline()).isFalse();
        assertThat(logged).singleElement().asString().contains("Steve");
    }

    @Test
    @DisplayName("marking cracked is refused in game unless opened; from the console the account turns cracked")
    void aMarkedCrackedNeedsTheConsole() {
        world.registered(server.addPlayer("Steve"), "hunter22");
        world.scheduler.settle(desk.markPremium(server.getConsoleSender(), "Steve"));
        logged.clear();

        world.scheduler.settle(desk.markCracked(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        staff, Keys.STAFF_CONSOLE_ONLY, Text.placeholder("command", "markcracked")));
        world.scheduler.settle(desk.markCracked(server.getConsoleSender(), "Steve"));

        assertThat(world.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .kind())
                .isEqualTo(com.uxplima.auth.domain.account.AccountKind.CRACKED);
        assertThat(logged).singleElement().asString().contains("Steve");
        opened.add(StaffAction.MARK_CRACKED);
        world.scheduler.settle(desk.markCracked(staff, "Steve"));
        assertThat(staff.nextComponentMessage())
                .isEqualTo(world.messages.render(staff, Keys.STAFF_MARK_ALREADY, Text.placeholder("name", "Steve")));
    }
}
