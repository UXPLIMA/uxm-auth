package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** How long a player may wait, and how often they are reminded while they do. */
final class WaitingClockTest {

    private static final Instant START = Instant.ofEpochMilli(1_760_000_000_000L);

    private ServerMock server;
    private DirectScheduler scheduler;
    private Sessions sessions;
    private MovableClock clock;
    private List<AuthSettings.Moment> fired;
    private WaitingClock waiting;
    private WaitingTimes times;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        scheduler = new DirectScheduler();
        sessions = new Sessions();
        clock = new MovableClock(START);
        fired = new ArrayList<>();
        Messages messages = new Messages(
                new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
        times = new WaitingTimes(
                sessions,
                () -> new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5)),
                clock);
        waiting = new WaitingClock(
                scheduler,
                sessions,
                () -> new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5)),
                clock,
                (Player player, AuthSettings.Moment moment) -> fired.add(moment),
                messages,
                times);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player who has not logged in by the login timeout is kicked, after the timeout effects")
    void timeoutKicks() {
        PlayerMock steve = registered("Steve");
        waiting.start(steve);

        clock.advance(Duration.ofSeconds(59));
        scheduler.tick();
        assertThat(steve.isOnline()).isTrue();

        clock.advance(Duration.ofSeconds(2));
        scheduler.tick();

        assertThat(fired).contains(AuthSettings.Moment.TIMEOUT);
        assertThat(steve.isOnline()).isFalse();
    }

    @Test
    @DisplayName("the time left counts down from the limit the player waits under, and is none once they are in")
    void theTimeLeftCountsDown() {
        PlayerMock steve = registered("Steve");
        PlayerMock alex = server.addPlayer("Alex");
        sessions.open(alex.getUniqueId(), new AuthState.Unregistered(new PlayerName("Alex")));
        waiting.start(steve);
        waiting.start(alex);

        clock.advance(Duration.ofSeconds(20));
        assertThat(times.left(steve.getUniqueId())).contains(Duration.ofSeconds(40));
        assertThat(times.left(alex.getUniqueId())).contains(Duration.ofSeconds(70));

        sessions.update(steve.getUniqueId(), state -> new AuthState.Authenticated(account(steve), AuthMethod.PASSWORD));
        assertThat(times.left(steve.getUniqueId()))
                .as("in, before the clock's next beat")
                .isEmpty();
        scheduler.tick();
        assertThat(times.left(steve.getUniqueId())).isEmpty();
        assertThat(times.left(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("a waiting player who left is let go at the next arrival, where no beat of theirs runs again")
    void aPlayerWhoLeftIsLetGo() {
        PlayerMock steve = registered("Steve");
        waiting.start(steve);
        // Folia runs no task of an entity that is gone, so the beat that would forget them never comes.
        sessions.close(steve.getUniqueId());

        PlayerMock alex = server.addPlayer("Alex");
        sessions.open(alex.getUniqueId(), new AuthState.Unregistered(new PlayerName("Alex")));
        waiting.start(alex);

        assertThat(times.waiting()).isEqualTo(1);
    }

    @Test
    @DisplayName("a player who logged in in time is not kicked, and the clock stops")
    void anAuthenticatedPlayerIsNotKicked() {
        PlayerMock steve = registered("Steve");
        waiting.start(steve);
        sessions.update(steve.getUniqueId(), state -> new AuthState.Authenticated(account(steve), AuthMethod.PASSWORD));

        clock.advance(Duration.ofMinutes(2));
        scheduler.tick();

        assertThat(steve.isOnline()).isTrue();
        assertThat(fired).doesNotContain(AuthSettings.Moment.TIMEOUT);
    }

    @Test
    @DisplayName("a player held to set up the app logged in already: the login clock neither kicks nor reminds them")
    void aPlayerSettingUpIsNotKicked() {
        PlayerMock steve = registered("Steve");
        waiting.start(steve);
        sessions.update(steve.getUniqueId(), state -> new AuthState.SettingUp(account(steve), AuthMethod.PASSWORD));

        clock.advance(Duration.ofMinutes(2));
        scheduler.tick();

        assertThat(steve.isOnline()).isTrue();
        assertThat(fired).isEmpty();
    }

    @Test
    @DisplayName("a player with no account has the longer register timeout")
    void registeringHasItsOwnTimeout() {
        PlayerMock alex = server.addPlayer("Alex");
        sessions.open(alex.getUniqueId(), new AuthState.Unregistered(new PlayerName("Alex")));
        waiting.start(alex);

        clock.advance(Duration.ofSeconds(75));
        scheduler.tick();
        assertThat(alex.isOnline()).isTrue();

        clock.advance(Duration.ofSeconds(16));
        scheduler.tick();
        assertThat(alex.isOnline()).isFalse();
    }

    @Test
    @DisplayName("the reminder for the player's own case repeats on its beat, not on every tick")
    void remindersRepeatOnTheirBeat() {
        PlayerMock steve = registered("Steve");
        waiting.start(steve);

        for (int second = 1; second <= 12; second++) {
            clock.advance(Duration.ofSeconds(1));
            scheduler.tick();
        }

        assertThat(fired).containsExactly(AuthSettings.Moment.REMINDER_LOGIN, AuthSettings.Moment.REMINDER_LOGIN);
    }

    @Test
    @DisplayName("a player who waits for the app's code is reminded of the code, not the password")
    void aPlayerWaitingForTheCodeIsRemindedOfTheCode() {
        PlayerMock steve = server.addPlayer("Steve");
        sessions.open(
                steve.getUniqueId(),
                new AuthState.SecondFactor(
                        account(steve),
                        com.uxplima.auth.domain.state.AuthMethod.SESSION,
                        java.util.Set.of(com.uxplima.auth.domain.factor.FactorKind.APP),
                        Attempts.fresh(3),
                        null));
        waiting.start(steve);

        for (int second = 1; second <= 6; second++) {
            clock.advance(Duration.ofSeconds(1));
            scheduler.tick();
        }

        assertThat(fired).containsExactly(AuthSettings.Moment.REMINDER_CODE);
    }

    private PlayerMock registered(String name) {
        PlayerMock player = server.addPlayer(name);
        sessions.open(player.getUniqueId(), new AuthState.Registered(account(player), Attempts.fresh(3)));
        return player;
    }

    private static Account account(Player player) {
        return new Account(
                player.getUniqueId(),
                new PlayerName(player.getName()),
                AccountKind.CRACKED,
                null,
                null,
                START,
                "127.0.0.1",
                null,
                null);
    }

    /** A clock a test moves by hand. */
    private static final class MovableClock extends Clock {

        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
