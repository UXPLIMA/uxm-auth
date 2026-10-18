package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Every login that lands here goes to the history, off the player's thread, and old ones are pruned. */
final class LoginRecordsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @TempDir
    Path folder;

    private AuthWorld world;
    private PlayerMock steve;
    private final List<Runnable> later = new ArrayList<>();
    private final List<String> warned = new ArrayList<>();

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private Account account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow();
    }

    private LoginRecords records(LoginHistory history) {
        return new LoginRecords(history, later::add, Clock.fixed(NOW, ZoneOffset.UTC), warned::add);
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
    }

    @Test
    @DisplayName("a login is written with its time, address and way in, and only once the task runs off the thread")
    void aLoginIsWrittenLater() {
        records(world.history).loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.SESSION));

        assertThat(world.history.recent(account().id(), 10)).isEmpty();
        later.forEach(Runnable::run);
        assertThat(world.history.recent(account().id(), 10))
                .containsExactly(new LoginHistory.Entry(NOW, address(), AuthMethod.SESSION));
    }

    @Test
    @DisplayName("the alerts hear of a login off the thread and before it is written, so it is not yet among the"
            + " accounts on its address; a failing read is told and the login is still written")
    void theAlertsHearItFirst() {
        List<String> told = new ArrayList<>();
        LoginRecords.Landed landed = (account, name, address) -> {
            told.add(name.value() + "@" + address + " written " + world.history.count(account));
            throw new IllegalStateException("the read failed");
        };
        LoginRecords records =
                new LoginRecords(world.history, later::add, Clock.fixed(NOW, ZoneOffset.UTC), warned::add, landed);

        records.loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.PASSWORD));
        assertThat(told).isEmpty();
        later.forEach(Runnable::run);
        later.clear();

        assertThat(told).containsExactly("Steve@" + address() + " written 0");
        assertThat(warned).singleElement().asString().contains("the read failed");
        assertThat(world.history.count(account().id())).isEqualTo(1);
        records.loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.NETWORK));
        later.forEach(Runnable::run);
        assertThat(told).hasSize(1);
    }

    @Test
    @DisplayName("every login here, the network's too, has its second factor read off the thread, and a player who"
            + " leaves has it forgotten")
    void theFactorIsKnownWhileThePlayerIsHere() {
        com.uxplima.auth.application.factor.FactorStates states =
                new com.uxplima.auth.application.factor.FactorStates(world.factors, world.emails);
        LoginRecords records = new LoginRecords(
                world.history,
                later::add,
                Clock.fixed(NOW, ZoneOffset.UTC),
                warned::add,
                (account, name, address) -> {},
                states);

        records.loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.NETWORK));
        assertThat(states.on(account().id())).isEmpty();
        later.forEach(Runnable::run);
        assertThat(states.on(account().id())).contains(false);

        records.left(new AuthState.Authenticated(account(), AuthMethod.NETWORK));
        assertThat(states.on(account().id())).isEmpty();
    }

    @Test
    @DisplayName("a login the network vouched for was written where it first landed, so nothing is written here")
    void aNetworkLoginIsNotWrittenTwice() {
        records(world.history).loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.NETWORK));

        assertThat(later).isEmpty();
    }

    @Test
    @DisplayName("a write that fails is told once in the console and never reaches the login")
    void aFailedWriteIsTold() {
        LoginHistory broken = new LoginHistory() {
            @Override
            public void record(UUID account, Entry entry) {
                throw new IllegalStateException("the database is gone");
            }

            @Override
            public List<Entry> recent(UUID account, int limit) {
                return List.of();
            }

            @Override
            public int count(UUID account) {
                return 0;
            }

            @Override
            public List<UUID> accountsOn(String address, int limit) {
                return List.of();
            }

            @Override
            public int prune(Instant before) {
                return 0;
            }
        };

        records(broken).loggedIn(steve, new AuthState.Authenticated(account(), AuthMethod.PASSWORD));
        later.forEach(Runnable::run);

        assertThat(warned).singleElement().asString().contains("the database is gone");
    }

    @Test
    @DisplayName("pruning drops every login older than the window the operator keeps")
    void pruneKeepsTheWindow() {
        world.history.record(
                account().id(), new LoginHistory.Entry(NOW.minus(Duration.ofDays(91)), "1.2.3.4", AuthMethod.PASSWORD));
        world.history.record(
                account().id(), new LoginHistory.Entry(NOW.minus(Duration.ofDays(1)), "1.2.3.4", AuthMethod.PASSWORD));

        records(world.history).prune(Duration.ofDays(90));

        assertThat(world.history.count(account().id())).isEqualTo(1);
    }

    @Test
    @DisplayName("the door writes the login it lets in")
    void theDoorWritesIt() {
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);

        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);

        assertThat(world.history.recent(account().id(), 10))
                .extracting(LoginHistory.Entry::method)
                .containsExactly(AuthMethod.PASSWORD);
    }

    @Test
    @DisplayName("the door has the second factor of the login it lets in read, and forgets it when the player leaves")
    void theDoorKeepsTheFactorWhileThePlayerStays() {
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        world.door.open(steve, AuthSettings.Moment.LOGGED_IN);

        assertThat(world.factorStates.on(account().id())).contains(false);
        world.door.leave(steve);
        assertThat(world.factorStates.on(account().id())).isEmpty();
    }
}
