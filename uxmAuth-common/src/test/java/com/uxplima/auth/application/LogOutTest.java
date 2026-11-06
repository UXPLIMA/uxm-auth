package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.factor.FactorKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class LogOutTest {

    private static final int ATTEMPTS = 3;
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");

    private final Sessions sessions = new Sessions();
    private final Remembered remembered = new Remembered();
    private final LogOut logOut = new LogOut(sessions, () -> ATTEMPTS, remembered);
    private final UUID connection = UUID.randomUUID();
    private final Account steve = account(new PasswordHash("$2a$04$hash"));

    private static Account account(@Nullable PasswordHash password) {
        return new Account(
                UUID.randomUUID(),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                password,
                NOW,
                "203.0.113.7",
                null,
                null);
    }

    private void standing(AuthState state) {
        sessions.open(connection, state);
    }

    private AuthState now() {
        return sessions.state(connection).orElseThrow();
    }

    @Test
    void endingSessionsLogsOutAPlayerWhoIsIn() {
        standing(new AuthState.Authenticated(steve, AuthMethod.PASSWORD));

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.LOGGED_OUT);
        assertThat(now()).isEqualTo(new AuthState.Registered(steve, Attempts.fresh(ATTEMPTS)));
    }

    @Test
    void endingSessionsSendsAPlayerAskedForANewPasswordBackToThePassword() {
        standing(new AuthState.Replacing(steve, new PasswordVerdict.Common()));

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.BACK_TO_PASSWORD);
        assertThat(now()).isEqualTo(new AuthState.Registered(steve, Attempts.fresh(ATTEMPTS)));
    }

    @Test
    void endingSessionsSendsAPlayerAskedForACodeBackToThePassword() {
        standing(new AuthState.SecondFactor(
                steve,
                AuthMethod.PASSWORD,
                Set.of(FactorKind.APP),
                Attempts.fresh(1).failed(),
                null));

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.BACK_TO_PASSWORD);
        assertThat(now()).isEqualTo(new AuthState.Registered(steve, Attempts.fresh(ATTEMPTS)));
    }

    @Test
    void endingSessionsSendsAPlayerSettingUpTheAppBackToThePassword() {
        standing(new AuthState.SettingUp(steve, AuthMethod.PASSWORD));

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.BACK_TO_PASSWORD);
        assertThat(now()).isEqualTo(new AuthState.Registered(steve, Attempts.fresh(ATTEMPTS)));
    }

    @Test
    void endingSessionsLeavesAPlayerWhoWaitsForThePassword() {
        AuthState waiting =
                new AuthState.Registered(steve, Attempts.fresh(ATTEMPTS).failed());
        standing(waiting);

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.UNTOUCHED);
        assertThat(now()).isEqualTo(waiting);
    }

    @Test
    void endingSessionsLeavesAnAccountWithNoPasswordAsItIs() {
        Account bedrock = account(null);
        AuthState waiting = new AuthState.SettingUp(bedrock, AuthMethod.BEDROCK);
        standing(waiting);

        assertThat(logOut.end(connection)).isEqualTo(LogOut.Ended.NO_PASSWORD);
        assertThat(now()).isEqualTo(waiting);
    }

    @Test
    void aLogoutStillLeavesAPlayerWhoIsNotInAsTheyAre() {
        AuthState waiting = new AuthState.Replacing(steve, new PasswordVerdict.Common());
        standing(waiting);

        assertThat(logOut.run(connection)).isEqualTo(LogOut.Result.NOT_LOGGED_IN);
        assertThat(now()).isEqualTo(waiting);
    }

    @Test
    void aLogoutForgetsTheRememberedSessions() {
        standing(new AuthState.Authenticated(steve, AuthMethod.PASSWORD));

        assertThat(logOut.run(connection)).isEqualTo(LogOut.Result.LOGGED_OUT);
        assertThat(remembered.forgotten).containsExactly(steve.id());
    }

    private static final class Remembered implements RememberedSessions {
        private final Set<UUID> forgotten = new HashSet<>();

        @Override
        public void remember(UUID account, String address, Instant until) {}

        @Override
        public boolean holds(UUID account, String address, Instant now) {
            return false;
        }

        @Override
        public void forget(UUID account) {
            forgotten.add(account);
        }

        @Override
        public Optional<Boolean> choice(UUID account) {
            return Optional.empty();
        }

        @Override
        public void choose(UUID account, boolean remember) {}
    }
}
