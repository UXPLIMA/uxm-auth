package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
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
    @DisplayName("a login bound to an opening another connection replaced is gone, and opens nothing")
    void aLoginForAnEarlierOpeningIsGone() {
        UUID connection = fixture.registered("Steve", "hunter22");
        long first = fixture.sessions.held(connection).orElseThrow().generation();
        fixture.sessions.open(connection, fixture.sessions.state(connection).orElseThrow());

        LoginResult result = useCase()
                .run(connection, first, Password.of("hunter22"), "10.0.0.7")
                .join();

        assertThat(result).isEqualTo(new LoginResult.Gone());
        assertThat(fixture.sessions.state(connection)).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a login bound to the opening that still holds the connection runs as usual")
    void aLoginForTheCurrentOpeningRuns() {
        UUID connection = fixture.registered("Steve", "hunter22");
        long current = fixture.sessions.held(connection).orElseThrow().generation();

        LoginResult result = useCase()
                .run(connection, current, Password.of("hunter22"), "10.0.0.7")
                .join();

        assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
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

    @Test
    @DisplayName("logins sent together cannot try more passwords than the limit allows")
    void concurrentLoginsCannotOutrunTheAttemptLimit() throws InterruptedException {
        UUID connection = fixture.registered("Steve", "hunter22");
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> blocker = fixture.lane.submit(() -> {
            busy.countDown();
            return await(release);
        });
        assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();

        List<CompletableFuture<LoginResult>> wrong = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            wrong.add(useCase().run(connection, Password.of("nope" + i + "234"), "10.0.0.7"));
        }
        CompletableFuture<LoginResult> right = useCase().run(connection, Password.of("hunter22"), "10.0.0.7");
        release.countDown();

        assertThat(blocker.join()).isTrue();
        assertThat(right.join()).isEqualTo(new LoginResult.TooManyAttempts());
        assertThat(wrong.stream().map(CompletableFuture::join))
                .filteredOn(LoginResult.Wrong.class::isInstance)
                .hasSize(2);
    }

    @Test
    @DisplayName("a password changed elsewhere after the join is the one checked, and it is not undone")
    void aPasswordChangedElsewhereIsNotUndone() {
        UUID connection = fixture.registered("Steve", "hunter22");
        Account changed = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .withPassword(fixture.hashing.hash(Password.of("changed99")));
        fixture.accounts.seed(changed);

        assertThat(login(connection, "hunter22")).isEqualTo(new LoginResult.Wrong(2));
        assertThat(login(connection, "changed99")).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(fixture.accounts
                        .findByName(new PlayerName("Steve"))
                        .orElseThrow()
                        .password())
                .isEqualTo(changed.password());
    }

    @Test
    @DisplayName("a player who rejoins under the same id does not inherit a login that was in flight")
    void aReconnectDoesNotInheritALoginInFlight() throws InterruptedException {
        UUID connection = fixture.registered("Steve", "hunter22");
        AuthState waiting = fixture.sessions.state(connection).orElseThrow();
        CountDownLatch busy = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Boolean> blocker = fixture.lane.submit(() -> {
            busy.countDown();
            return await(release);
        });
        assertThat(busy.await(5, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<LoginResult> pending = useCase().run(connection, Password.of("hunter22"), "10.0.0.7");
        fixture.sessions.close(connection);
        fixture.sessions.open(connection, waiting);
        release.countDown();

        assertThat(blocker.join()).isTrue();
        assertThat(pending.join()).isEqualTo(new LoginResult.Gone());
        assertThat(fixture.sessions.state(connection)).containsInstanceOf(AuthState.Registered.class);
        assertThat(fixture.accounts.writes()).isZero();
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
