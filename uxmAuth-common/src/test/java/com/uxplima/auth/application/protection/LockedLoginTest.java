package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.MemoryAccounts;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LockedLoginTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String HOME = "203.0.113.7";
    private static final BanSteps STEPS = new BanSteps(List.of(Duration.ofMinutes(1)), 3, Duration.ofHours(24));

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final HashingLane lane = new HashingLane(1, 1, 8);
    private final Sessions sessions = new Sessions();
    private final MemoryAccounts accounts = new MemoryAccounts();
    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);
    private final AccountLockoutTest.MemoryTallies tallies = new AccountLockoutTest.MemoryTallies();
    private final LogIn logIn = new LogIn(
            accounts,
            hashing,
            lane,
            sessions,
            Clock.fixed(NOW, ZoneOffset.UTC),
            (address, host) -> Optional.empty(),
            () -> PasswordPolicy.ANY,
            LogIn.Captcha.NONE,
            SecondStep.NONE,
            AuthEvents.NONE,
            new AccountLockout(tallies, () -> STEPS, SharedAddresses::none, now::get));
    private final Account steve = new Account(
            UUID.randomUUID(),
            new PlayerName("Steve"),
            AccountKind.CRACKED,
            null,
            hashing.hash(Password.of("hunter22")),
            NOW.minus(Duration.ofDays(3)),
            HOME,
            NOW.minus(Duration.ofDays(1)),
            HOME);

    @AfterEach
    void stopTheLane() {
        lane.close();
    }

    @Test
    void wrongPasswordsOverManyConnectionsLockTheAccountUntilItsTimeRunsOut() {
        accounts.insert(steve);

        assertThat(logInFresh("nope", "192.0.2.1")).isEqualTo(new LoginResult.Wrong(2));
        assertThat(logInFresh("nope", "192.0.2.2")).isEqualTo(new LoginResult.Wrong(2));
        assertThat(logInFresh("nope", "192.0.2.3")).isEqualTo(new LoginResult.Locked(Duration.ofMinutes(1)));
        assertThat(logInFresh("hunter22", "192.0.2.4")).isEqualTo(new LoginResult.Locked(Duration.ofMinutes(1)));

        now.set(NOW.plus(Duration.ofMinutes(1)));
        assertThat(logInFresh("hunter22", "192.0.2.4")).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(tallies.find(steve.id())).isEmpty();
    }

    @Test
    void theOwnerOnTheirLastAddressIsNotHeldBackByALockStrangersMade() {
        accounts.insert(steve);
        logInFresh("nope", "192.0.2.1");
        logInFresh("nope", "192.0.2.2");
        logInFresh("nope", "192.0.2.3");

        assertThat(logInFresh("hunter22", HOME)).isInstanceOf(LoginResult.LoggedIn.class);
    }

    private LoginResult logInFresh(String typed, String address) {
        UUID connection = UUID.randomUUID();
        sessions.open(connection, new AuthState.Registered(steve, Attempts.fresh(3)));
        return logIn.run(connection, Password.of(typed), address).join();
    }
}
