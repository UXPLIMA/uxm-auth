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

    @Test
    @DisplayName("every opening carries a visit of its own, or the one it came with, and keeps it through a change")
    void everyOpeningCarriesAVisit() {
        Sessions sessions = new Sessions();
        UUID id = UUID.randomUUID();
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));
        String first = sessions.held(id).orElseThrow().visit();
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));
        String second = sessions.held(id).orElseThrow().visit();

        assertThat(first).isNotBlank().isNotEqualTo(second);

        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")), "from-the-proxy");
        sessions.update(id, state -> state);
        assertThat(sessions.held(id).orElseThrow().visit()).isEqualTo("from-the-proxy");
    }

    @Test
    @DisplayName("a choice to be remembered is kept through a change of state and not by the next opening")
    void aChoiceToBeRememberedBelongsToTheOpening() {
        Sessions sessions = new Sessions();
        UUID id = UUID.randomUUID();
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(sessions.held(id).orElseThrow().chose()).isFalse();

        sessions.chose(id, true);
        sessions.update(id, state -> state);
        long generation = sessions.held(id).orElseThrow().generation();
        sessions.update(id, generation, state -> state);
        assertThat(sessions.held(id).orElseThrow().chose()).isTrue();

        sessions.chose(id, false);
        assertThat(sessions.held(id).orElseThrow().chose()).isFalse();

        sessions.chose(id, true);
        sessions.open(id, new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(sessions.held(id).orElseThrow().chose()).isFalse();

        sessions.chose(UUID.randomUUID(), true);
        assertThat(sessions.held(UUID.randomUUID())).isEmpty();
    }
}
