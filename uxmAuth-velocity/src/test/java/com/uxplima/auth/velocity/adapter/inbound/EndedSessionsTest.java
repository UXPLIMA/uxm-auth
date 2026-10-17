package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An account whose sessions ended waits again on the proxy, unless its player made the change. */
final class EndedSessionsTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");

    private final ProxySessions sessions = new ProxySessions();
    private final EndedSessions ended = new EndedSessions(sessions);

    private void onAServer(String visit) {
        sessions.waiting(STEVE, new Claim.Waiting(true, false, false));
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);
        sessions.wrote(STEVE, visit);
        sessions.arrived(STEVE);
    }

    @Test
    @DisplayName("a logged-in player whose sessions all ended waits again under the door's flags")
    void aLoginEnds() {
        onAServer("v1");

        ended.accept(new Bulletin.SessionsEnded(STEVE, Optional.empty()));

        assertThat(sessions.loggedIn(STEVE)).isFalse();
        assertThat(sessions.door(STEVE)).contains(new Claim.Waiting(true, false, false));
    }

    @Test
    @DisplayName("the session that made the change stays logged in, and a second one of the account does not")
    void onlyTheChangingSessionStays() {
        onAServer("v1");
        ended.accept(new Bulletin.SessionsEnded(STEVE, Optional.of("v1")));
        assertThat(sessions.loggedIn(STEVE)).isTrue();

        ended.accept(new Bulletin.SessionsEnded(STEVE, Optional.of("v-elsewhere")));
        assertThat(sessions.loggedIn(STEVE)).isFalse();
    }
}
