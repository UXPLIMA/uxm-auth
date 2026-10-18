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
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.application.staff.StaffAction;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
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
        desk = new StaffDesk(gate, force, world.bans, world.lane, world.replies, world.messages, logged::add);
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
    @DisplayName("a forced login reaches the waiting player through the bulletins and lets them in as forced")
    void aForcedLoginLetsThePlayerIn() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");

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
}
