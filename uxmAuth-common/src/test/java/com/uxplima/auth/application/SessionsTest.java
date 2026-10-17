package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class SessionsTest {

    @Test
    @DisplayName("a close names the opening it means, and a later opening under the same id survives it")
    void aCloseOfAnOldOpeningLeavesTheNewOne() {
        Sessions sessions = new Sessions();
        UUID id = UUID.randomUUID();
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));
        long first = sessions.held(id).orElseThrow().generation();
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));

        sessions.close(id, first);

        assertThat(sessions.isOpen(id)).isTrue();

        sessions.close(id, sessions.held(id).orElseThrow().generation());

        assertThat(sessions.isOpen(id)).isFalse();
    }
}
