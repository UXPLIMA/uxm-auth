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
import com.uxplima.auth.testing.InMemoryDisputes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Staff settle a name both a cracked holder and a paid owner want, under the block strategy. */
final class ResolveConflictTest {

    private static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final PlayerName NOTCH = new PlayerName("Notch");

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryDisputes disputes = new InMemoryDisputes();
    private final List<Bulletin> published = new ArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            published.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };
    private final ResolveConflict resolve =
            new ResolveConflict(fixture.accounts, disputes, () -> "_", events, fixture.clock);

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID holder() {
        fixture.registered("Notch", "hunter22");
        disputes.raise(NOTCH, MOJANG, AuthFixture.NOW);
        return fixture.accounts.findByName(NOTCH).orElseThrow().id();
    }

    @Test
    @DisplayName("for the paid owner: the cracked holder moves to the marked name with everything, and rejoins")
    void thePaidOwnerTakesTheName() {
        UUID holder = holder();

        assertThat(resolve.resolve(new PlayerName("notch"), ResolveConflict.Side.PREMIUM))
                .isEqualTo(ResolveConflict.Resolved.PREMIUM_TAKES);

        assertThat(fixture.accounts.findById(holder).orElseThrow().name().value())
                .isEqualTo("Notch_");
        assertThat(fixture.accounts.findByName(NOTCH)).isEmpty();
        assertThat(disputes.of(NOTCH)).isEmpty();
        assertThat(published).containsExactly(new Bulletin.Rejoin(holder));
    }

    @Test
    @DisplayName("for the cracked holder: the holder keeps the name and the dispute is kept as settled")
    void theCrackedHolderKeepsTheName() {
        UUID holder = holder();

        assertThat(resolve.resolve(NOTCH, ResolveConflict.Side.CRACKED))
                .isEqualTo(ResolveConflict.Resolved.CRACKED_KEEPS);

        assertThat(fixture.accounts.findByName(NOTCH).orElseThrow().id()).isEqualTo(holder);
        assertThat(disputes.of(NOTCH))
                .hasValueSatisfying(dispute -> assertThat(dispute.kept()).isTrue());
        assertThat(published).isEmpty();
    }

    @Test
    @DisplayName("a name in no dispute is said so, and nothing changes")
    void noDispute() {
        fixture.registered("Notch", "hunter22");

        assertThat(resolve.resolve(NOTCH, ResolveConflict.Side.PREMIUM)).isEqualTo(ResolveConflict.Resolved.NO_DISPUTE);
        assertThat(fixture.accounts.findByName(NOTCH)).isPresent();
    }
}
