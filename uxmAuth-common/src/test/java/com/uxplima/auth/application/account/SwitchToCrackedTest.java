package com.uxplima.auth.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A premium player turns their own account cracked, and never without a password to log in with afterwards. */
final class SwitchToCrackedTest {

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

    private final SwitchToCracked cracked = new SwitchToCracked(
            fixture.sessions,
            fixture.accounts,
            fixture.hashing,
            fixture.lane,
            new ChangePassword(
                    fixture.accounts,
                    fixture.hashing,
                    fixture.lane,
                    fixture.sessions,
                    () -> fixture.policy,
                    events,
                    fixture.remembered),
            new MarkKind(fixture.accounts, name -> new PremiumAnswer.Unknown(), UuidScheme.OFFLINE, events));

    @AfterEach
    void close() {
        fixture.close();
    }

    /** A logged-in connection of an account of this kind, with this password or none. */
    private UUID loggedIn(String name, AccountKind kind, @Nullable String password) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                kind,
                kind == AccountKind.PREMIUM ? UUID.randomUUID() : null,
                password == null ? null : fixture.hashing.hash(Password.of(password)),
                AuthFixture.NOW,
                "",
                null,
                null);
        fixture.accounts.seed(account);
        UUID connection = UUID.randomUUID();
        fixture.sessions.open(connection, new AuthState.Authenticated(account, AuthMethod.PREMIUM));
        return connection;
    }

    private Account account(String name) {
        return fixture.accounts.findByName(new PlayerName(name)).orElseThrow();
    }

    @Test
    @DisplayName("a premium account with a password turns cracked when the password is typed, and rejoins")
    void theRightPasswordSwitches() {
        UUID notch = loggedIn("Notch", AccountKind.PREMIUM, "hunter22");

        assertThat(cracked.confirm(notch, "hunter22").join()).isEqualTo(new SwitchToCracked.Switched.Done());
        assertThat(account("Notch").kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(published).contains(new Bulletin.Rejoin(account("Notch").id(), Bulletin.By.OWNER));
    }

    @Test
    @DisplayName("a wrong password switches nothing")
    void aWrongPasswordSwitchesNothing() {
        UUID notch = loggedIn("Notch", AccountKind.PREMIUM, "hunter22");

        assertThat(cracked.confirm(notch, "hunter23").join()).isEqualTo(new SwitchToCracked.Switched.WrongPassword());
        assertThat(account("Notch").kind()).isEqualTo(AccountKind.PREMIUM);
    }

    @Test
    @DisplayName("a premium account with no password takes the typed one as its first, then turns cracked")
    void noPasswordTakesTheTypedOne() {
        UUID notch = loggedIn("Notch", AccountKind.PREMIUM, null);

        assertThat(cracked.confirm(notch, "hunter22").join()).isEqualTo(new SwitchToCracked.Switched.Done());
        Account after = account("Notch");
        assertThat(after.kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(fixture.hashing.verify(Password.of("hunter22"), java.util.Objects.requireNonNull(after.password())))
                .isNotInstanceOf(com.uxplima.auth.domain.hash.Verification.Wrong.class);
    }

    @Test
    @DisplayName("a first password the rules refuse is told why, and the account stays premium with none")
    void aRefusedFirstPasswordSwitchesNothing() {
        UUID notch = loggedIn("Notch", AccountKind.PREMIUM, null);

        assertThat(cracked.confirm(notch, "abc").join())
                .isEqualTo(new SwitchToCracked.Switched.Refused(new PasswordVerdict.TooShort(6)));
        assertThat(account("Notch").kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(account("Notch").password()).isNull();
    }

    @Test
    @DisplayName("a cracked or Bedrock account is told it is not premium, and a player not logged in is told to log in")
    void onlyALoggedInPremiumOwnerSwitches() {
        UUID steve = loggedIn("Steve", AccountKind.CRACKED, "hunter22");
        assertThat(cracked.confirm(steve, "hunter22").join()).isEqualTo(new SwitchToCracked.Switched.NotPremium());

        UUID bedrock = loggedIn("Bedrock_Jo", AccountKind.BEDROCK, null);
        assertThat(cracked.confirm(bedrock, "hunter22").join()).isEqualTo(new SwitchToCracked.Switched.NotPremium());
        assertThat(account("Bedrock_Jo").password())
                .as("no password is set on the way")
                .isNull();

        UUID waiting = fixture.registered("Alex", "hunter22");
        assertThat(cracked.confirm(waiting, "hunter22").join()).isEqualTo(new SwitchToCracked.Switched.NotLoggedIn());
    }
}
