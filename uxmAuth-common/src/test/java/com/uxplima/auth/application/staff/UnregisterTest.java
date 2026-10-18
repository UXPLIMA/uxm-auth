package com.uxplima.auth.application.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff remove an account: it is gone, and every member hears it so its player is sent away. */
final class UnregisterTest {

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
    private final Unregister unregister = new Unregister(fixture.accounts, events);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("the account is deleted and every member hears it; an unknown name removes nothing")
    void theAccountGoes() {
        fixture.registered("Steve", "hunter22");
        UUID steve = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();

        assertThat(unregister.remove(new PlayerName("steve"))).isEqualTo(Unregister.Removed.REMOVED);
        assertThat(fixture.accounts.findById(steve)).isEmpty();
        assertThat(published).containsExactly(new Bulletin.Unregistered(steve));

        assertThat(unregister.remove(new PlayerName("Steve"))).isEqualTo(Unregister.Removed.UNKNOWN);
        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("an account staff removed is told to the listeners once, under its own name")
    void aRemovalIsTold() {
        fixture.registered("Steve", "hunter22");
        com.uxplima.auth.testing.HeardEvents listeners = new com.uxplima.auth.testing.HeardEvents();
        Unregister told = new Unregister(fixture.accounts, events, listeners);

        told.remove(new PlayerName("steve"));
        told.remove(new PlayerName("steve"));

        assertThat(listeners.heard).containsExactly("unregistered Steve");
    }
}
