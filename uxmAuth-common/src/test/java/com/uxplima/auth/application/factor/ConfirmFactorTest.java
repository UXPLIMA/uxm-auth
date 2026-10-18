package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.FactorKind;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryFactors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The code after the password: once, inside its window, with tries that run out and count against the address. */
final class ConfirmFactorTest {

    private static final Totp TOTP = new Totp(6, Duration.ofSeconds(30), 1);
    private static final TotpSecret SECRET = new TotpSecret(new byte[20]);

    private final AuthFixture fixture = new AuthFixture(1);
    private final InMemoryFactors factors = new InMemoryFactors();
    private final List<String> failed = new ArrayList<>();
    private @Nullable Duration ban;
    private final LogIn.Failures failures = (address, host) -> {
        failed.add(address + "@" + host);
        return Optional.ofNullable(ban);
    };
    private Instant now = AuthFixture.NOW;
    private final FactorCheck check = new FactorCheck(
            factors, fixture.hashing, () -> TOTP, () -> new BackupCodes(8, 10), FactorCheck.MailedCode.NONE);
    private final ConfirmFactor confirm =
            new ConfirmFactor(fixture.accounts, fixture.sessions, check, failures, () -> now);
    private final Account steve = AuthFixture.account("Steve", new PasswordHash("$hash$x"));

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID waiting(AuthMethod method, @Nullable PasswordVerdict weak) {
        fixture.accounts.seed(steve);
        factors.setApp(steve.id(), SECRET);
        UUID connection = UUID.randomUUID();
        fixture.sessions.open(
                connection,
                new AuthState.SecondFactor(steve, method, Set.of(FactorKind.APP), Attempts.fresh(3), weak),
                "visit",
                "play.example.net");
        return connection;
    }

    private String codeAt(Instant at) {
        return TOTP.code(SECRET.bytes(), TOTP.step(at));
    }

    @Test
    @DisplayName("the app's code logs in by the first step's way, and records the login")
    void theCodeLogsIn() {
        UUID connection = waiting(AuthMethod.SESSION, null);

        ConfirmFactor.Result result = confirm.confirm(connection, codeAt(now), "203.0.113.7");

        assertThat(result).isInstanceOf(ConfirmFactor.Result.LoggedIn.class);
        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(AuthState.Authenticated.class, in -> {
                            assertThat(in.method()).isEqualTo(AuthMethod.SESSION);
                            assertThat(in.account().lastAddress()).isEqualTo("203.0.113.7");
                        }));
        assertThat(fixture.accounts.findById(steve.id())).hasValueSatisfying(account -> {
            assertThat(account.lastLoginAt()).isEqualTo(now);
            assertThat(account.lastAddress()).isEqualTo("203.0.113.7");
        });
    }

    @Test
    @DisplayName("a weak password that waited behind the code is to be replaced once the code passes")
    void aWeakPasswordIsReplacedAfter() {
        PasswordVerdict weak = new PasswordVerdict.TooShort(10);
        UUID connection = waiting(AuthMethod.PASSWORD, weak);

        confirm.confirm(connection, codeAt(now), "203.0.113.7");

        assertThat(fixture.sessions.state(connection))
                .hasValueSatisfying(state -> assertThat(state)
                        .isInstanceOfSatisfying(
                                AuthState.Replacing.class,
                                replacing -> assertThat(replacing.why()).isEqualTo(weak)));
    }

    @Test
    @DisplayName("a code one step either side of now is taken, two steps off is not")
    void theWindowIsOneStep() {
        UUID early = waiting(AuthMethod.PASSWORD, null);
        assertThat(confirm.confirm(early, codeAt(now.minusSeconds(60)), "203.0.113.7"))
                .isInstanceOf(ConfirmFactor.Result.Wrong.class);
        assertThat(confirm.confirm(early, codeAt(now.minusSeconds(30)), "203.0.113.7"))
                .isInstanceOf(ConfirmFactor.Result.LoggedIn.class);

        UUID late = waiting(AuthMethod.PASSWORD, null);
        assertThat(confirm.confirm(late, codeAt(now.plusSeconds(30)), "203.0.113.7"))
                .isInstanceOf(ConfirmFactor.Result.LoggedIn.class);
    }

    @Test
    @DisplayName("a code used once is refused inside its window, on this connection or another")
    void aCodeIsTakenOnce() {
        UUID first = waiting(AuthMethod.PASSWORD, null);
        String code = codeAt(now);
        assertThat(confirm.confirm(first, code, "203.0.113.7")).isInstanceOf(ConfirmFactor.Result.LoggedIn.class);

        UUID second = UUID.randomUUID();
        fixture.sessions.open(
                second,
                new AuthState.SecondFactor(
                        steve, AuthMethod.PASSWORD, Set.of(FactorKind.APP), Attempts.fresh(3), null));

        assertThat(confirm.confirm(second, code, "203.0.113.7")).isEqualTo(new ConfirmFactor.Result.Wrong(2));
    }

    @Test
    @DisplayName("a wrong code counts down and against the address and host, and the last try is too many")
    void wrongCodesCountDown() {
        UUID connection = waiting(AuthMethod.PASSWORD, null);

        assertThat(confirm.confirm(connection, "000000", "203.0.113.7")).isEqualTo(new ConfirmFactor.Result.Wrong(2));
        assertThat(confirm.confirm(connection, "000000", "203.0.113.7")).isEqualTo(new ConfirmFactor.Result.Wrong(1));
        assertThat(confirm.confirm(connection, "000000", "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.TooManyAttempts());
        assertThat(confirm.confirm(connection, codeAt(now), "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.TooManyAttempts());
        assertThat(failed)
                .containsExactly(
                        "203.0.113.7@play.example.net", "203.0.113.7@play.example.net", "203.0.113.7@play.example.net");
    }

    @Test
    @DisplayName("a wrong code that bans the address says for how long")
    void aBanIsSaid() {
        UUID connection = waiting(AuthMethod.PASSWORD, null);
        ban = Duration.ofMinutes(5);

        assertThat(confirm.confirm(connection, "000000", "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.Banned(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("a connection that waits for no code, or is gone, is told so and nothing counts")
    void nothingWaits() {
        UUID registered = fixture.registered("Alex", "hunter22");

        assertThat(confirm.confirm(registered, "123456", "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.NotWaiting());
        assertThat(confirm.confirm(UUID.randomUUID(), "123456", "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.Gone());
        assertThat(failed).isEmpty();
    }

    @Test
    @DisplayName("an app turned off meanwhile leaves no code to take: the try is wrong")
    void anAppTurnedOffMeanwhile() {
        UUID connection = waiting(AuthMethod.PASSWORD, null);
        factors.removeApp(steve.id());

        assertThat(confirm.confirm(connection, codeAt(now), "203.0.113.7"))
                .isEqualTo(new ConfirmFactor.Result.Wrong(2));
    }

    @Test
    @DisplayName("a backup code logs in once; typed again it is a wrong code")
    void aBackupCodeLogsInOnce() {
        UUID first = waiting(AuthMethod.PASSWORD, null);
        List<String> codes = check.newBackupCodes(steve.id(), new SplittableRandom(9));

        assertThat(confirm.confirm(first, codes.get(2).toLowerCase(Locale.ROOT), "203.0.113.7"))
                .isInstanceOf(ConfirmFactor.Result.LoggedIn.class);
        UUID second = UUID.randomUUID();
        fixture.sessions.open(
                second,
                new AuthState.SecondFactor(steve, AuthMethod.PASSWORD, Set.of(FactorKind.APP), Attempts.fresh(3), null),
                "visit",
                "play.example.net");
        assertThat(confirm.confirm(second, codes.get(2), "203.0.113.7")).isEqualTo(new ConfirmFactor.Result.Wrong(2));
        assertThat(factors.backupCodes(steve.id())).hasSize(7);
    }
}
