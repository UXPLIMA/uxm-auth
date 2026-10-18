package com.uxplima.auth.application.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import com.uxplima.auth.application.port.Mailer;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryEmails;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A forgotten password: a code to the linked address, and the code with a new password sets it. */
final class RecoverPasswordTest {

    private static final EmailAddress TO =
            EmailAddress.parse("berke@example.com").orElseThrow();

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryEmails emails = new InMemoryEmails();
    private final List<Map<String, String>> mailed = new ArrayList<>();
    private final List<Mailer.Kind> kinds = new ArrayList<>();
    private final List<String> failed = new ArrayList<>();
    private final List<Bulletin> heard = new CopyOnWriteArrayList<>();
    private @Nullable Duration ban;
    private Instant now = AuthFixture.NOW;
    private boolean breached;
    private final MailCodes codes = new MailCodes(
            emails,
            (kind, to, language, values) -> {
                kinds.add(kind);
                mailed.add(values);
                return CompletableFuture.completedFuture(Mailer.Outcome.SENT);
            },
            fixture.hashing,
            fixture.lane,
            () -> new MailCodes.Settings(Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
            (address, host) -> {
                failed.add(address);
                return Optional.ofNullable(ban);
            },
            new SplittableRandom(21),
            () -> now);
    private final RecoverPassword recover = new RecoverPassword(
            fixture.sessions,
            emails,
            codes,
            fixture.accounts,
            fixture.hashing,
            fixture.lane,
            () -> fixture.policy,
            password -> breached,
            fixture.remembered,
            new NetworkEvents() {
                @Override
                public void publish(Bulletin bulletin) {
                    heard.add(bulletin);
                }

                @Override
                public void listen(Consumer<Bulletin> listener) {}
            });

    @AfterEach
    void close() {
        fixture.close();
    }

    private Account steve() {
        return fixture.accounts.findByName(new PlayerName("Steve")).orElseThrow();
    }

    private UUID waiting() {
        UUID connection = fixture.registered("Steve", "hunter22");
        emails.setAddress(steve().id(), TO);
        return connection;
    }

    private String code() {
        return Objects.requireNonNull(mailed.getLast().get("code"), "code");
    }

    private RecoverPassword.Outcome recover(UUID connection, String code, String fresh) {
        return recover.recover(connection, code, Password.of(fresh), "203.0.113.7", "play.example.net")
                .join();
    }

    @Test
    @DisplayName("a player at the login gets a recovery code at the linked address, said masked")
    void aCodeIsMailed() {
        UUID connection = waiting();

        assertThat(recover.request(connection, Locale.ENGLISH).join())
                .isEqualTo(new RecoverPassword.Outcome.Sent("b***@e***.com", Duration.ofMinutes(15)));
        assertThat(kinds).containsExactly(Mailer.Kind.RECOVERY);
        assertThat(mailed.getLast()).containsEntry("player", "Steve");
    }

    @Test
    @DisplayName("a second request inside the cooldown sends nothing and says when")
    void theCooldownSaysWhen() {
        UUID connection = waiting();
        recover.request(connection, Locale.ENGLISH).join();
        now = now.plusSeconds(45);

        assertThat(recover.request(connection, Locale.ENGLISH).join())
                .isEqualTo(new RecoverPassword.Outcome.Cooldown(Duration.ofSeconds(15)));
        assertThat(mailed).hasSize(1);
    }

    @Test
    @DisplayName("the code and a new password set it, end every other session, and leave the player at the login")
    void theCodeSetsANewPassword() {
        UUID connection = waiting();
        fixture.remembered.remember(steve().id(), "10.0.0.7", now.plusSeconds(60));
        recover.request(connection, Locale.ENGLISH).join();

        assertThat(recover(connection, code(), "fresh7777")).isEqualTo(new RecoverPassword.Outcome.Recovered());
        assertThat(fixture.remembered.until)
                .as("a remembered session is no way back in")
                .isEmpty();

        assertThat(fixture.hashing.verify(Password.of("fresh7777"), Objects.requireNonNull(steve().password())))
                .isNotInstanceOf(Verification.Wrong.class);
        assertThat(fixture.hashing.verify(Password.of("hunter22"), Objects.requireNonNull(steve().password())))
                .isInstanceOf(Verification.Wrong.class);
        assertThat(heard).containsExactly(new Bulletin.SessionsEnded(steve().id(), Optional.empty()));
        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(
                                AuthState.Registered.class,
                                held -> assertThat(held.account().password()).isEqualTo(steve().password())));
    }

    @Test
    @DisplayName("a wrong code counts toward the ban and says the tries left; a ban is said")
    void aWrongCodeCounts() {
        UUID connection = waiting();
        recover.request(connection, Locale.ENGLISH).join();

        assertThat(recover(connection, "000000", "fresh7777")).isEqualTo(new RecoverPassword.Outcome.Wrong(4));
        assertThat(failed).containsExactly("203.0.113.7");
        ban = Duration.ofMinutes(30);
        assertThat(recover(connection, "000000", "fresh7777"))
                .isEqualTo(new RecoverPassword.Outcome.Banned(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("an expired code is refused even when right, and the password stays")
    void anExpiredCodeIsRefused() {
        UUID connection = waiting();
        recover.request(connection, Locale.ENGLISH).join();
        now = now.plus(Duration.ofMinutes(16));

        assertThat(recover(connection, code(), "fresh7777")).isEqualTo(new RecoverPassword.Outcome.Expired());
        assertThat(fixture.hashing.verify(Password.of("hunter22"), Objects.requireNonNull(steve().password())))
                .isNotInstanceOf(Verification.Wrong.class);
    }

    @Test
    @DisplayName("a new password the rules refuse is said, and spends no try of the code")
    void aWeakPasswordSpendsNothing() {
        UUID connection = waiting();
        recover.request(connection, Locale.ENGLISH).join();

        assertThat(recover(connection, code(), "abc"))
                .isEqualTo(new RecoverPassword.Outcome.Rejected(new PasswordVerdict.TooShort(6)));
        assertThat(recover(connection, code(), "fresh7777")).isEqualTo(new RecoverPassword.Outcome.Recovered());
    }

    @Test
    @DisplayName("a new password known from a breach is refused before the code is checked, and spends no try")
    void aBreachedPasswordSpendsNothing() {
        UUID connection = waiting();
        recover.request(connection, Locale.ENGLISH).join();
        breached = true;

        assertThat(recover(connection, code(), "fresh7777"))
                .isEqualTo(new RecoverPassword.Outcome.Rejected(new PasswordVerdict.Breached()));
        assertThat(failed).isEmpty();
        breached = false;
        assertThat(recover(connection, code(), "fresh7777")).isEqualTo(new RecoverPassword.Outcome.Recovered());
    }

    @Test
    @DisplayName("no linked address, or a player not at the login, gets no code")
    void onlyAtTheLoginWithAnAddress() {
        UUID connection = fixture.registered("Steve", "hunter22");
        assertThat(recover.request(connection, Locale.ENGLISH).join())
                .isEqualTo(new RecoverPassword.Outcome.NoAddress());

        emails.setAddress(steve().id(), TO);
        fixture.sessions.update(connection, state -> new AuthState.Authenticated(steve(), AuthMethod.PASSWORD));
        assertThat(recover.request(connection, Locale.ENGLISH).join())
                .isEqualTo(new RecoverPassword.Outcome.NotWaiting());
        assertThat(recover.request(UUID.randomUUID(), Locale.ENGLISH).join())
                .isEqualTo(new RecoverPassword.Outcome.NotWaiting());
        assertThat(mailed).isEmpty();
    }
}
