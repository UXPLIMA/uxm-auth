package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What a moved account's player is told, and where a connection under the old name is sent. */
final class MovesTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final Moves moves = new Moves(fixture.accounts);

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a connection under an old name reads where its account went, and the player is told once")
    void aMoveIsReadAndToldOnce() {
        Account steve = AuthFixture.account("Notch", fixture.hashing.hash(Password.of("hunter22")));
        fixture.accounts.seed(steve);
        fixture.accounts.rename(steve.id(), new PlayerName("Notch_"), AuthFixture.NOW);

        assertThat(moves.movedTo(new PlayerName("notch"))).contains(new PlayerName("Notch_"));
        assertThat(moves.untold(steve.id()))
                .contains(new AccountRepository.Renamed(new PlayerName("Notch"), new PlayerName("Notch_")));

        moves.told(steve.id());
        assertThat(moves.untold(steve.id())).isEmpty();
    }
}
