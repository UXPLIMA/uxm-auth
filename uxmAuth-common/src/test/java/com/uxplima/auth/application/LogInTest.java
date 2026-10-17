package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class LogInTest {

    private final AuthFixture fixture = new AuthFixture(1);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("the right password logs the player in and records where from")
    void loginWithTheRightPasswordAuthenticates() {
        UUID connection = fixture.registered("Steve", "hunter22");

        LoginResult result = login(connection, "hunter22");

        assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(
                                AuthState.Authenticated.class,
                                logged -> assertThat(logged.method()).isEqualTo(AuthMethod.PASSWORD)));
        assertThat(fixture.accounts.findByName(new PlayerName("Steve"))).hasValueSatisfying(account -> {
            assertThat(account.lastLoginAt()).isEqualTo(AuthFixture.NOW);
            assertThat(account.lastAddress()).isEqualTo("10.0.0.7");
        });
    }

    @Test
    @DisplayName("each wrong password counts down, and the last one is too many")
    void loginWrongCountsDown() {
        UUID connection = fixture.registered("Steve", "hunter22");

        assertThat(login(connection, "nope1234")).isEqualTo(new LoginResult.Wrong(2));
        assertThat(login(connection, "nope1234")).isEqualTo(new LoginResult.Wrong(1));
        assertThat(login(connection, "nope1234")).isEqualTo(new LoginResult.TooManyAttempts());
        assertThat(login(connection, "hunter22")).isEqualTo(new LoginResult.TooManyAttempts());
    }

    @Test
    @DisplayName("a right password on an old hash is stored again in the current algorithm")
    void loginRehashesALegacyHashAndStoresIt() {
        Account legacy = AuthFixture.account("Alex", new BcryptHasher(4).hash(Password.of("hunter22")));
        fixture.accounts.seed(legacy);
        UUID connection = UUID.randomUUID();
        fixture.sessions.open(connection, new AuthState.Registered(legacy, Attempts.fresh(3)));

        assertThat(login(connection, "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);

        assertThat(fixture.accounts.findByName(new PlayerName("Alex")))
                .hasValueSatisfying(account -> assertThat(
                                Objects.requireNonNull(account.password()).encoded())
                        .startsWith("$argon2id$"));
    }

    @Test
    @DisplayName("a player who left while the hash ran gets nothing written and nothing opened")
    void aPlayerWhoLeftDuringTheHashGetsNothingWritten() throws InterruptedException {
        UUID connection = fixture.registered("Steve", "hunter22");
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> blocker = fixture.lane.submit(() -> {
            busy.countDown();
            return await(release);
        });
        assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<LoginResult> pending = useCase().run(connection, Password.of("hunter22"), "10.0.0.7");
        fixture.sessions.close(connection);
        release.countDown();

        assertThat(pending.join()).isEqualTo(new LoginResult.Gone());
        assertThat(blocker.join()).isTrue();
        assertThat(fixture.accounts.writes()).isZero();
        assertThat(fixture.sessions.isOpen(connection)).isFalse();
    }

    @Test
    @DisplayName("a name with no account is told to register")
    void anUnregisteredPlayerIsToldToRegister() {
        assertThat(login(fixture.unregistered("Steve"), "hunter22")).isEqualTo(new LoginResult.NotRegistered());
    }

    @Test
    @DisplayName("a connection nobody holds answers gone")
    void anUnknownConnectionIsGone() {
        assertThat(login(UUID.randomUUID(), "hunter22")).isEqualTo(new LoginResult.Gone());
    }

    @Test
    @DisplayName("a player already logged in is told so and nothing is checked")
    void aLoggedInPlayerIsToldSo() {
        UUID connection = fixture.registered("Steve", "hunter22");
        login(connection, "hunter22");

        assertThat(login(connection, "whatever")).isEqualTo(new LoginResult.AlreadyLoggedIn());
    }

    private LoginResult login(UUID connection, String password) {
        return useCase().run(connection, Password.of(password), "10.0.0.7").join();
    }

    private LogIn useCase() {
        return new LogIn(fixture.accounts, fixture.hashing, fixture.lane, fixture.sessions, fixture.clock);
    }

    private static boolean await(CountDownLatch latch) {
        try {
            return latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
