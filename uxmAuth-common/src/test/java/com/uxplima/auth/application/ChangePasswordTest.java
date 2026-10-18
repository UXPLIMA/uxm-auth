package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.testing.AuthFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class ChangePasswordTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private final List<Bulletin> heard = new CopyOnWriteArrayList<>();
    private final NetworkEvents events = new NetworkEvents() {
        @Override
        public void publish(Bulletin bulletin) {
            heard.add(bulletin);
        }

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    };

    @AfterEach
    void close() {
        fixture.close();
    }

    @Test
    @DisplayName("a change without the old password changes nothing")
    void changeNeedsTheOldPassword() {
        UUID connection = loggedIn("Steve", "hunter22");

        assertThat(change(connection, "wrong999", "fresh777")).isEqualTo(new ChangeResult.WrongOld());
        assertThat(login(connection, "hunter22")).isTrue();
    }

    @Test
    @DisplayName("the new password meets the same policy as a first one")
    void changeAppliesThePolicy() {
        UUID connection = loggedIn("Steve", "hunter22");

        assertThat(change(connection, "hunter22", "abc"))
                .isEqualTo(new ChangeResult.Rejected(new PasswordVerdict.TooShort(6)));
    }

    @Test
    @DisplayName("a change is stored and the old password stops working")
    void changeIsStored() {
        UUID connection = loggedIn("Steve", "hunter22");

        assertThat(change(connection, "hunter22", "fresh777")).isEqualTo(new ChangeResult.Changed());

        PasswordHash stored = Objects.requireNonNull(fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .password());
        assertThat(fixture.hashing.verify(Password.of("fresh777"), stored)).isNotInstanceOf(Verification.Wrong.class);
        assertThat(fixture.hashing.verify(Password.of("hunter22"), stored)).isInstanceOf(Verification.Wrong.class);
    }

    @Test
    @DisplayName(
            "a change tells every member of the network that every session of the account but this one ended, and a refused one tells nobody")
    void aChangeEndsTheOtherSessions() {
        UUID connection = loggedIn("Steve", "hunter22");
        UUID account = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();

        change(connection, "wrong999", "fresh777");
        assertThat(heard).isEmpty();

        fixture.remembered.remember(account, "10.0.0.7", fixture.clock.instant().plusSeconds(60));
        change(connection, "hunter22", "fresh777");
        assertThat(fixture.remembered.until).isEmpty();
        String visit = fixture.sessions.held(connection).orElseThrow().visit();
        assertThat(heard).containsExactly(new Bulletin.SessionsEnded(account, java.util.Optional.of(visit)));
    }

    @Test
    @DisplayName("a session whose visit cannot be named keeps nothing: every session of the account ends")
    void anUnnamableVisitEndsEverySession() {
        UUID connection = loggedIn("Steve", "hunter22");
        fixture.sessions.open(connection, fixture.sessions.state(connection).orElseThrow(), "not a visit");
        UUID account = fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .id();

        assertThat(change(connection, "hunter22", "fresh777")).isEqualTo(new ChangeResult.Changed());
        assertThat(heard).containsExactly(new Bulletin.SessionsEnded(account, java.util.Optional.empty()));
    }

    @Test
    @DisplayName("a player who has not logged in cannot change a password")
    void changeNeedsALogin() {
        UUID connection = fixture.registered("Steve", "hunter22");

        assertThat(change(connection, "hunter22", "fresh777")).isEqualTo(new ChangeResult.NotLoggedIn());
    }

    @Test
    @DisplayName("the old password is checked against what is stored now, not what was read at the join")
    void changeChecksTheStoredPassword() {
        UUID connection = loggedIn("Steve", "hunter22");
        fixture.accounts.seed(fixture.accounts
                .findByName(new PlayerName("Steve"))
                .orElseThrow()
                .withPassword(fixture.hashing.hash(Password.of("changed99"))));

        assertThat(change(connection, "hunter22", "fresh777")).isEqualTo(new ChangeResult.WrongOld());
        assertThat(change(connection, "changed99", "fresh777")).isEqualTo(new ChangeResult.Changed());
    }

    @Test
    @DisplayName("a server that does not ask for the old password replaces it on the policy alone")
    void aReplaceSkipsTheOldPassword() {
        UUID connection = loggedIn("Steve", "hunter22");
        ChangePassword useCase = new ChangePassword(
                fixture.accounts,
                fixture.hashing,
                fixture.lane,
                fixture.sessions,
                () -> fixture.policy,
                events,
                fixture.remembered);

        assertThat(useCase.replace(connection, Password.of("abc")).join())
                .isEqualTo(new ChangeResult.Rejected(new PasswordVerdict.TooShort(6)));
        assertThat(useCase.replace(connection, Password.of("fresh777")).join()).isEqualTo(new ChangeResult.Changed());
        assertThat(login(connection, "fresh777")).isTrue();
    }

    @Test
    @DisplayName("a new password known from a breach is refused after the old one was checked, and nothing changes")
    void aBreachedPasswordIsRefused() {
        UUID connection = loggedIn("Steve", "hunter22");
        java.util.List<String> asked = new java.util.ArrayList<>();
        ChangePassword useCase = new ChangePassword(
                fixture.accounts,
                fixture.hashing,
                fixture.lane,
                fixture.sessions,
                () -> fixture.policy,
                events,
                fixture.remembered,
                password -> {
                    asked.add("asked");
                    return true;
                });

        assertThat(useCase.run(connection, Password.of("wrong123"), Password.of("fresh777"))
                        .join())
                .isEqualTo(new ChangeResult.WrongOld());
        assertThat(asked).isEmpty();
        assertThat(useCase.run(connection, Password.of("hunter22"), Password.of("fresh777"))
                        .join())
                .isEqualTo(new ChangeResult.Rejected(new PasswordVerdict.Breached()));
        assertThat(login(connection, "hunter22")).isTrue();
    }

    @Test
    @DisplayName("a replace still needs a login")
    void aReplaceNeedsALogin() {
        UUID connection = fixture.registered("Steve", "hunter22");

        assertThat(new ChangePassword(
                                fixture.accounts,
                                fixture.hashing,
                                fixture.lane,
                                fixture.sessions,
                                () -> fixture.policy,
                                events,
                                fixture.remembered)
                        .replace(connection, Password.of("fresh777"))
                        .join())
                .isEqualTo(new ChangeResult.NotLoggedIn());
    }

    private UUID loggedIn(String name, String password) {
        UUID connection = fixture.registered(name, password);
        assertThat(login(connection, password)).isTrue();
        return connection;
    }

    private boolean login(UUID connection, String password) {
        new LogOut(fixture.sessions, () -> AuthFixture.MAX_ATTEMPTS, fixture.remembered).run(connection);
        return new LogIn(fixture.accounts, fixture.hashing, fixture.lane, fixture.sessions, fixture.clock)
                        .run(connection, Password.of(password), "10.0.0.7")
                        .join()
                instanceof LoginResult.LoggedIn;
    }

    private ChangeResult change(UUID connection, String old, String fresh) {
        return new ChangePassword(
                        fixture.accounts,
                        fixture.hashing,
                        fixture.lane,
                        fixture.sessions,
                        () -> fixture.policy,
                        events,
                        fixture.remembered)
                .run(connection, Password.of(old), Password.of(fresh))
                .join();
    }
}
