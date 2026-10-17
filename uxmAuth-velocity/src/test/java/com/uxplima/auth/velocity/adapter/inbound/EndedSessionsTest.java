package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    @DisplayName("a logged-in player whose sessions ended waits again under the door's flags")
    void aLoginEnds() {
        sessions.waiting(STEVE, new Claim.Waiting(true, false, false));
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);

        ended.accept(new Bulletin.SessionsEnded(STEVE, false));

        assertThat(sessions.loggedIn(STEVE)).isFalse();
        assertThat(sessions.door(STEVE)).contains(new Claim.Waiting(true, false, false));
    }

    @Test
    @DisplayName("the player who changed the password themselves stays logged in")
    void theChangingLoginStays() {
        sessions.waiting(STEVE, new Claim.Waiting(false, false, false));
        sessions.loggedIn(STEVE, AuthMethod.PASSWORD);

        ended.accept(new Bulletin.SessionsEnded(STEVE, true));

        assertThat(sessions.loggedIn(STEVE)).isTrue();
    }
}
