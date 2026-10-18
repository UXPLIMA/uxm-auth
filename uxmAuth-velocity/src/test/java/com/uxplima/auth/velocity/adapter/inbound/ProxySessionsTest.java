package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.state.AuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The proxy's own record of who is in, and how, told once to whoever listens for logins. */
final class ProxySessionsTest {

    private final List<String> told = new ArrayList<>();
    private final ProxySessions sessions = new ProxySessions((id, method) -> told.add(method.name()));
    private final UUID steve = UUID.randomUUID();

    @Test
    @DisplayName("a login is told once, whoever decided it, and its way is read back while the player is in")
    void aLoginIsToldOnce() {
        sessions.waiting(steve, new Claim.Waiting(false, false, false));
        assertThat(sessions.way(steve)).isEmpty();

        sessions.loggedIn(steve, AuthMethod.PASSWORD);
        sessions.reported(steve, AuthMethod.PASSWORD);
        assertThat(sessions.way(steve)).contains(AuthMethod.PASSWORD);

        sessions.loggedOut(steve);
        assertThat(sessions.way(steve)).isEmpty();
        sessions.reported(steve, AuthMethod.SESSION);

        assertThat(told).containsExactly("PASSWORD", "SESSION");
    }

    @Test
    @DisplayName("a player owing the app's code is not in yet, and nobody is told")
    void aCodeIsNotALogin() {
        sessions.code(steve, AuthMethod.PREMIUM);

        assertThat(sessions.way(steve)).isEmpty();
        assertThat(told).isEmpty();
    }
}
