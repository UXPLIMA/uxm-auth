package com.uxplima.auth.application.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.ChangeResult;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.MemoryAccounts;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.account.DeleteOwnAccount;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.factor.BackupCodes;
import com.uxplima.auth.domain.factor.Totp;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** A wrong old password and a wrong proof of deletion cost what a wrong login does. */
class WrongProofsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String ADDRESS = "203.0.113.7";
    private static final BanSteps STEPS = new BanSteps(List.of(Duration.ofMinutes(1)), 3, Duration.ofHours(24));

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of());
    private final HashingLane lane = new HashingLane(1, 1, 8);
    private final Sessions sessions = new Sessions();
    private final MemoryAccounts accounts = new MemoryAccounts();
    private final AccountLockoutTest.MemoryTallies tallies = new AccountLockoutTest.MemoryTallies();
    private final LogIn.Lockout lockout = new AccountLockout(tallies, () -> STEPS, SharedAddresses::none, () -> NOW);
    private final AtomicInteger addressFailures = new AtomicInteger();
    private final LogIn.Failures failures = (address, host) -> {
        addressFailures.incrementAndGet();
        return Optional.empty();
    };
    private final UUID connection = UUID.randomUUID();
    private final Account steve = new Account(
            UUID.randomUUID(),
            new PlayerName("Steve"),
            AccountKind.CRACKED,
            null,
            hashing.hash(Password.of("hunter22")),
            NOW.minus(Duration.ofDays(3)),
            ADDRESS,
            NOW,
            ADDRESS);

    @AfterEach
    void stopTheLane() {
        lane.close();
    }

    @Test
    void wrongOldPasswordsCountAndLockTheChange() {
        loggedIn();
        ChangePassword change = new ChangePassword(
                accounts,
                hashing,
                lane,
                sessions,
                () -> PasswordPolicy.ANY,
                new Silent(),
                new NoSessions(),
                BreachedPasswords.NONE,
                AuthEvents.NONE,
                failures,
                lockout);

        assertThat(change(change, "nope")).isEqualTo(new ChangeResult.WrongOld());
        assertThat(change(change, "nope")).isEqualTo(new ChangeResult.WrongOld());
        assertThat(change(change, "nope")).isEqualTo(new ChangeResult.Locked(Duration.ofMinutes(1)));
        assertThat(change(change, "hunter22")).isEqualTo(new ChangeResult.Locked(Duration.ofMinutes(1)));
        assertThat(addressFailures).hasValue(3);
        assertThat(accounts.findById(steve.id()).orElseThrow().password()).isEqualTo(steve.password());
    }

    @Test
    void aRightOldPasswordGivesItsTryBack() {
        loggedIn();
        ChangePassword change = new ChangePassword(
                accounts,
                hashing,
                lane,
                sessions,
                () -> PasswordPolicy.ANY,
                new Silent(),
                new NoSessions(),
                BreachedPasswords.NONE,
                AuthEvents.NONE,
                failures,
                lockout);

        change(change, "nope");
        assertThat(change(change, "hunter22")).isEqualTo(new ChangeResult.Changed());
        assertThat(tallies.find(steve.id()).orElseThrow().failures()).isEqualTo(1);
    }

    @Test
    void wrongProofsOfDeletionCountAndLock() {
        loggedIn();
        DeleteOwnAccount delete = new DeleteOwnAccount(
                sessions,
                accounts,
                new NoFactors(),
                new FactorCheck(
                        new NoFactors(),
                        hashing,
                        () -> new Totp(6, Duration.ofSeconds(30), 1),
                        () -> new BackupCodes(8, 10),
                        FactorCheck.MailedCode.NONE),
                hashing,
                lane,
                new Unregister(accounts, new Silent()),
                () -> Duration.ofMinutes(1),
                Clock.fixed(NOW, ZoneOffset.UTC),
                new DeleteOwnAccount.Guards(failures, lockout));

        for (int tried = 0; tried < 2; tried++) {
            delete.ask(connection);
            assertThat(delete.confirm(connection, "nope", ADDRESS).join())
                    .isEqualTo(new DeleteOwnAccount.Deleted.WrongProof());
        }
        delete.ask(connection);
        assertThat(delete.confirm(connection, "nope", ADDRESS).join())
                .isEqualTo(new DeleteOwnAccount.Deleted.Locked(Duration.ofMinutes(1)));
        delete.ask(connection);
        assertThat(delete.confirm(connection, "hunter22", ADDRESS).join())
                .isEqualTo(new DeleteOwnAccount.Deleted.Locked(Duration.ofMinutes(1)));
        assertThat(accounts.findById(steve.id())).isPresent();
        assertThat(addressFailures).hasValue(3);
    }

    private void loggedIn() {
        accounts.insert(steve);
        sessions.open(connection, new AuthState.Authenticated(steve, AuthMethod.PASSWORD));
    }

    private ChangeResult change(ChangePassword change, String old) {
        return change.run(connection, Password.of(old), Password.of("a new one"), ADDRESS)
                .join();
    }

    /** Bulletins that go nowhere. */
    private static final class Silent implements NetworkEvents {
        @Override
        public void publish(Bulletin bulletin) {}

        @Override
        public void listen(Consumer<Bulletin> listener) {}
    }

    /** No remembered sessions at all. */
    private static final class NoSessions implements RememberedSessions {
        @Override
        public void remember(UUID account, String address, Instant until) {}

        @Override
        public boolean holds(UUID account, String address, Instant now) {
            return false;
        }

        @Override
        public void forget(UUID account) {}

        @Override
        public Optional<Boolean> choice(UUID account) {
            return Optional.empty();
        }

        @Override
        public void choose(UUID account, boolean remember) {}
    }

    /** An account with no app and no backup codes. */
    private static final class NoFactors implements Factors {
        @Override
        public Optional<App> app(UUID account) {
            return Optional.empty();
        }

        @Override
        public void setApp(UUID account, TotpSecret secret) {}

        @Override
        public void removeApp(UUID account) {}

        @Override
        public boolean useStep(UUID account, long step) {
            return false;
        }

        @Override
        public List<PasswordHash> backupCodes(UUID account) {
            return List.of();
        }

        @Override
        public void setBackupCodes(UUID account, List<PasswordHash> codes) {}

        @Override
        public boolean useBackupCode(UUID account, PasswordHash code) {
            return false;
        }
    }
}
