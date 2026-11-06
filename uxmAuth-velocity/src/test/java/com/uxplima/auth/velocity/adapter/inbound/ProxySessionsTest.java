package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.Test;

class ProxySessionsTest {

    private final ProxySessions sessions = new ProxySessions();
    private final UUID id = UUID.randomUUID();

    @Test
    void theFirstServerReadsHowThePlayerCameInAndTheNextOnesReadTheNetwork() {
        sessions.loggedIn(id, AuthMethod.PASSWORD);

        assertThat(sessions.nextStanding(id)).contains(new Claim.LoggedIn(AuthMethod.PASSWORD));
        assertThat(sessions.nextStanding(id)).contains(new Claim.LoggedIn(AuthMethod.NETWORK));
    }

    @Test
    void aLoginARememberedSessionMadeStaysOneOnEveryServer() {
        sessions.loggedIn(id, AuthMethod.SESSION);

        assertThat(sessions.nextStanding(id)).contains(new Claim.LoggedIn(AuthMethod.SESSION));
        assertThat(sessions.nextStanding(id)).contains(new Claim.LoggedIn(AuthMethod.NETWORK_SESSION));
        assertThat(AuthMethod.NETWORK_SESSION.remembered()).isTrue();
        assertThat(AuthMethod.NETWORK_SESSION.fromTheNetwork()).isTrue();
    }
}
