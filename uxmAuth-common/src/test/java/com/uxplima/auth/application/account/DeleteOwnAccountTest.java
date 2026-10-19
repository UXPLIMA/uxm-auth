package com.uxplima.auth.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryFactors;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A player removes their own account only by asking and then proving, inside a window, that it is theirs. */
final class DeleteOwnAccountTest {

    private static final Totp TOTP = new Totp(6, Duration.ofSeconds(30), 1);
    private static final TotpSecret SECRET = new TotpSecret(new byte[20]);

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryFactors factors = new InMemoryFactors();
    private final TurnedClock clock = new TurnedClock(Instant.parse("2026-10-19T12:00:00Z"));
    private final List<Bulletin> published = new ArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            published.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };

    private final DeleteOwnAccount delete = new DeleteOwnAccount(
            fixture.sessions,
            fixture.accounts,
            factors,
            new FactorCheck(factors, fixture.hashing, () -> TOTP, () -> new BackupCodes(8, 10), (id, code) -> false),
            fixture.hashing,
            fixture.lane,
            new Unregister(fixture.accounts, events),
            () -> Duration.ofSeconds(60),
            clock);

    @AfterEach
    void close() {
        fixture.close();
    }

    /** A logged-in connection of an account with this password. */
    private UUID loggedIn(String name, String password) {
        UUID connection = fixture.registered(name, password);
        Account account = fixture.accounts.findByName(new PlayerName(name)).orElseThrow();
        fixture.sessions.open(connection, new AuthState.Authenticated(account, AuthMethod.PASSWORD));
        return connection;
    }

    private boolean exists(String name) {
        return fixture.accounts.findByName(new PlayerName(name)).isPresent();
    }

    @Test
    @DisplayName("asked, then the right password inside the window: the account is gone, and every member hears it")
    void theRightPasswordDeletes() {
        UUID steve = loggedIn("Steve", "hunter22");
        UUID id = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();

        assertThat(delete.ask(steve)).isEqualTo(new DeleteOwnAccount.Deleted.Asked(Duration.ofSeconds(60), false));
        clock.turn(Duration.ofSeconds(59));

        assertThat(delete.confirm(steve, "hunter22").join()).isEqualTo(new DeleteOwnAccount.Deleted.Removed());
        assertThat(exists("Steve")).isFalse();
        assertThat(published).containsExactly(new Bulletin.Unregistered(id, Bulletin.By.OWNER));
    }

    @Test
    @DisplayName("a wrong password deletes nothing and closes the window, so the next try asks again")
    void aWrongPasswordDeletesNothing() {
        UUID steve = loggedIn("Steve", "hunter22");
        delete.ask(steve);

        assertThat(delete.confirm(steve, "hunter23").join()).isEqualTo(new DeleteOwnAccount.Deleted.WrongProof());
        assertThat(delete.confirm(steve, "hunter22").join()).isEqualTo(new DeleteOwnAccount.Deleted.NotAsked());
        assertThat(exists("Steve")).isTrue();
    }

    @Test
    @DisplayName("a confirmation after the window, or with no ask before it, is refused and deletes nothing")
    void aLateConfirmationIsRefused() {
        UUID steve = loggedIn("Steve", "hunter22");
        assertThat(delete.confirm(steve, "hunter22").join()).isEqualTo(new DeleteOwnAccount.Deleted.NotAsked());

        delete.ask(steve);
        clock.turn(Duration.ofSeconds(61));

        assertThat(delete.confirm(steve, "hunter22").join()).isEqualTo(new DeleteOwnAccount.Deleted.NotAsked());
        assertThat(exists("Steve")).isTrue();
    }

    @Test
    @DisplayName("an ask nobody confirmed is let go at the next ask once its window has closed")
    void anUnconfirmedAskIsLetGo() {
        UUID steve = loggedIn("Steve", "hunter22");
        UUID alex = loggedIn("Alex", "hunter33");
        delete.ask(steve);
        clock.turn(Duration.ofSeconds(61));

        delete.ask(alex);

        assertThat(delete.open()).isEqualTo(1);
    }

    @Test
    @DisplayName("an account with the app confirms by its code, and its password is no proof then")
    void anAppAccountConfirmsByCode() {
        UUID steve = loggedIn("Steve", "hunter22");
        UUID id = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();
        factors.setApp(id, SECRET);

        assertThat(delete.ask(steve)).isEqualTo(new DeleteOwnAccount.Deleted.Asked(Duration.ofSeconds(60), true));
        assertThat(delete.confirm(steve, "hunter22").join()).isEqualTo(new DeleteOwnAccount.Deleted.WrongProof());

        delete.ask(steve);
        String code = TOTP.code(SECRET.bytes(), TOTP.step(clock.instant()));
        assertThat(delete.confirm(steve, code).join()).isEqualTo(new DeleteOwnAccount.Deleted.Removed());
        assertThat(exists("Steve")).isFalse();
    }

    @Test
    @DisplayName("a player who has not logged in cannot ask, and an account with nothing to prove it by is told so")
    void onlyALoggedInOwnerWithAProofAsks() {
        UUID waiting = fixture.registered("Steve", "hunter22");
        assertThat(delete.ask(waiting)).isEqualTo(new DeleteOwnAccount.Deleted.NotLoggedIn());

        UUID notch = UUID.randomUUID();
        Account premium = new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                com.uxplima.auth.domain.account.AccountKind.PREMIUM,
                UUID.randomUUID(),
                null,
                AuthFixture.NOW,
                "",
                null,
                null);
        fixture.accounts.seed(premium);
        fixture.sessions.open(notch, new AuthState.Authenticated(premium, AuthMethod.PREMIUM));
        assertThat(delete.ask(notch)).isEqualTo(new DeleteOwnAccount.Deleted.NoProof());
    }
}
