package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff let a waiting player in: the network is asked, and the server they wait on lets them in as forced. */
final class ForceLoginTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final List<Bulletin> published = new ArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            published.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };
    private final ForceLogin force = new ForceLogin(fixture.accounts, fixture.sessions, events, fixture.clock);

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID account(String name) {
        return fixture.accounts.findByName(new PlayerName(name)).orElseThrow().id();
    }

    @Test
    @DisplayName("staff asking for an account's player tells every server; an unknown name asks nobody")
    void askingTellsTheNetwork() {
        fixture.registered("Steve", "hunter22");

        assertThat(force.ask(new PlayerName("steve"))).isEqualTo(ForceLogin.Asked.ASKED);
        assertThat(published).containsExactly(new Bulletin.LetIn(account("Steve")));
        assertThat(force.ask(new PlayerName("Nobody"))).isEqualTo(ForceLogin.Asked.UNKNOWN);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("a waiting player is let in as forced, and the login is kept on the account")
    void aWaitingPlayerIsLetIn() {
        UUID connection = fixture.registered("Steve", "hunter22");

        AuthState.Authenticated in = force.admit(connection, "203.0.113.7").orElseThrow();

        assertThat(in.method()).isEqualTo(AuthMethod.FORCED);
        assertThat(fixture.sessions.state(connection)).contains(in);
        assertThat(fixture.accounts.findById(account("Steve")).orElseThrow().lastAddress())
                .isEqualTo("203.0.113.7");
    }

    @Test
    @DisplayName("a player with no account, one already in, or no session at all is left as they are")
    void othersAreLeftAlone() {
        UUID stranger = fixture.unregistered("Alex");
        UUID steve = fixture.registered("Steve", "hunter22");
        force.admit(steve, "203.0.113.7");
        AuthState before = fixture.sessions.state(steve).orElseThrow();

        assertThat(force.admit(stranger, "203.0.113.7")).isEmpty();
        assertThat(fixture.sessions.state(stranger).orElseThrow()).isInstanceOf(AuthState.Unregistered.class);
        assertThat(force.admit(steve, "198.51.100.2")).isEmpty();
        assertThat(fixture.sessions.state(steve)).contains(before);
        assertThat(force.admit(UUID.randomUUID(), "203.0.113.7")).isEmpty();
    }
}
