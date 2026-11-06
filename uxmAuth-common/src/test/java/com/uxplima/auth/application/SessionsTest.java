package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.Test;

class SessionsTest {

    private final Sessions sessions = new Sessions();
    private final UUID connection = UUID.randomUUID();
    private final AuthState waiting = new AuthState.Unregistered(new PlayerName("Steve"), false);

    @Test
    void eachOpeningAnswersAGenerationOfItsOwn() {
        long first = sessions.open(connection, waiting);
        long second = sessions.open(connection, waiting);

        assertThat(first).isPositive();
        assertThat(second).isNotEqualTo(first);
        assertThat(sessions.isOpen(connection, second)).isTrue();
        assertThat(sessions.isOpen(connection, first)).isFalse();
    }

    @Test
    void anEarlierOpeningClosesNothingOfALaterOne() {
        long first = sessions.open(connection, waiting);
        long second = sessions.open(connection, waiting);

        assertThat(sessions.close(connection, first)).isFalse();
        assertThat(sessions.isOpen(connection, second)).isTrue();
        assertThat(sessions.close(connection, second)).isTrue();
        assertThat(sessions.isOpen(connection)).isFalse();
    }
}
