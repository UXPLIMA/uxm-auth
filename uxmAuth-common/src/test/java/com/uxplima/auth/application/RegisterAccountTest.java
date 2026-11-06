package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.lane.LaneFullException;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RegisterAccountTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String ADDRESS = "203.0.113.7";

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final HashingLane lane = new HashingLane(2, 2, 8);
    private final Sessions sessions = new Sessions();
    private final LogInTest.Accounts accounts = new LogInTest.Accounts();
    private final UUID connection = UUID.randomUUID();
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch started = new CountDownLatch(1);

    @AfterEach
    void stopTheLane() {
        release.countDown();
        lane.close();
    }

    /** A breach check that holds the first registration on the lane until the test lets it go. */
    private RegisterAccount register() {
        return new RegisterAccount(
                accounts,
                hashing,
                lane,
                sessions,
                () -> PasswordPolicy.ANY,
                Clock.fixed(NOW, ZoneOffset.UTC),
                (address, host) -> false,
                password -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return false;
                });
    }

    private CompletableFuture<RegisterResult> answer(RegisterAccount register) {
        return register.run(
                connection, new PlayerName("Steve"), Password.of("hunter22"), Password.of("hunter22"), ADDRESS);
    }

    @Test
    void aSecondAnswerWhileTheFirstIsOnTheLaneIsTurnedAwayWithoutQueueingAHash() throws InterruptedException {
        sessions.open(connection, new AuthState.Unregistered(new PlayerName("Steve"), false));
        RegisterAccount register = register();

        CompletableFuture<RegisterResult> first = answer(register);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<RegisterResult> second = answer(register);

        assertThat(second).isCompletedExceptionally();
        try {
            second.join();
        } catch (CompletionException expected) {
            assertThat(expected.getCause()).isInstanceOf(LaneFullException.class);
        }
        release.countDown();
        assertThat(first.join()).isInstanceOf(RegisterResult.Created.class);
    }

    @Test
    void theConnectionMayAnswerAgainOnceTheFirstAnswerIsDone() {
        sessions.open(connection, new AuthState.Unregistered(new PlayerName("Steve"), false));
        release.countDown();
        RegisterAccount register = register();

        assertThat(answer(register).join()).isInstanceOf(RegisterResult.Created.class);
        assertThat(answer(register).join()).isInstanceOf(RegisterResult.AlreadyRegistered.class);
    }
}
