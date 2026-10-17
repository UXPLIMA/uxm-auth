package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class LogOutTest {

    private final AuthFixture fixture = new AuthFixture(1);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a logout forgets the login and gives every attempt back")
    void logoutForgetsAuthentication() {
        UUID connection = fixture.registered("Steve", "hunter22");
        new LogIn(fixture.accounts, fixture.hashing, fixture.lane, fixture.sessions, fixture.clock)
                .run(connection, Password.of("hunter22"), "10.0.0.7")
                .join();

        assertThat(new LogOut(fixture.sessions, () -> 5).run(connection)).isTrue();

        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(
                                AuthState.Registered.class,
                                waiting -> assertThat(waiting.attempts().left()).isEqualTo(5)));
    }

    @Test
    @DisplayName("a player who is not logged in has nothing to log out of")
    void nothingToLogOutOf() {
        UUID connection = fixture.registered("Steve", "hunter22");

        assertThat(new LogOut(fixture.sessions, () -> 5).run(connection)).isFalse();
    }
}
