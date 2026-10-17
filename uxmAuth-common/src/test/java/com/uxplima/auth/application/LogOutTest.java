package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
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

        assertThat(new LogOut(fixture.sessions, () -> 5).run(connection)).isEqualTo(LogOut.Result.LOGGED_OUT);

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

        assertThat(new LogOut(fixture.sessions, () -> 5).run(connection)).isEqualTo(LogOut.Result.NOT_LOGGED_IN);
    }

    @Test
    @DisplayName("an account with no password is not sent back to a door it has no key to")
    void anAccountWithNoPasswordStaysIn() {
        UUID connection = UUID.randomUUID();
        AuthState state = new LetBedrockIn(fixture.accounts, () -> 5, fixture.clock)
                .admit(connection, new PlayerName(".Steve"), false, "10.0.0.7");
        fixture.sessions.open(connection, state);

        assertThat(new LogOut(fixture.sessions, () -> 5).run(connection)).isEqualTo(LogOut.Result.NO_PASSWORD);
        assertThat(fixture.sessions.state(connection)).containsInstanceOf(AuthState.Authenticated.class);
    }
}
