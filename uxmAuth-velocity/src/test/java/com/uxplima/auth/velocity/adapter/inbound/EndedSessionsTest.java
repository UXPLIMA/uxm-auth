package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.Test;

class EndedSessionsTest {

    private final ProxySessions sessions = new ProxySessions();
    private final EndedSessions ended = new EndedSessions(sessions);
    private final UUID id = UUID.randomUUID();

    @Test
    void endingEverySessionLogsOutAPlayerNoServerLetInYet() {
        sessions.loggedIn(id, AuthMethod.PASSWORD);

        ended.accept(new Bulletin.SessionsEnded(id, Optional.empty()));

        assertThat(sessions.loggedIn(id)).isFalse();
    }

    @Test
    void theVisitThatMadeTheChangeStaysLoggedIn() {
        sessions.loggedIn(id, AuthMethod.PASSWORD);
        sessions.wrote(id, "visit");
        sessions.arrived(id);

        ended.accept(new Bulletin.SessionsEnded(id, Optional.of("visit")));

        assertThat(sessions.loggedIn(id)).isTrue();
    }

    @Test
    void anotherVisitIsLoggedOut() {
        sessions.loggedIn(id, AuthMethod.PASSWORD);
        sessions.wrote(id, "visit");
        sessions.arrived(id);

        ended.accept(new Bulletin.SessionsEnded(id, Optional.of("another")));

        assertThat(sessions.loggedIn(id)).isFalse();
    }
}
