package com.uxplima.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.AuthMeSha256Hasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.hash.HashFormats;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LogInTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String ADDRESS = "203.0.113.7";
    private static final PasswordHash AUTHME_HUNTER22 =
            new PasswordHash("$SHA$a1b2c3d4e5f6a7b8$db136337234cc82d6d0363b07ec32e8cd4a8fc7900ba6b1b227f1d6e6afc50d4");

    private final Hashing hashing = new Hashing(new BcryptHasher(4), List.of(new AuthMeSha256Hasher()));
    private final HashingLane lane = new HashingLane(1, 1, 8);
    private final Sessions sessions = new Sessions();
    private final Accounts accounts = new Accounts();
    private final UUID connection = UUID.randomUUID();

    @AfterEach
    void stopTheLane() {
        lane.close();
    }

    private Account register(PasswordHash password) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                password,
                NOW.minus(Duration.ofDays(3)),
                ADDRESS,
                null,
                null);
        accounts.insert(account);
        return account;
    }

    private void waitForLogin(Account account, int attempts) {
        sessions.open(connection, new AuthState.Registered(account, Attempts.fresh(attempts)));
    }

    private LoginResult logIn(LogIn logIn, String typed) {
        return logIn.run(connection, Password.of(typed), ADDRESS).join();
    }

    private LogIn logIn() {
        return new LogIn(accounts, hashing, lane, sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void theRightPasswordLogsInAndRecordsTheVisit() {
        Account steve = register(hashing.hash(Password.of("hunter22")));
        waitForLogin(steve, 3);

        LoginResult result = logIn(logIn(), "hunter22");

        assertThat(result).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(sessions.state(connection)).containsInstanceOf(AuthState.Authenticated.class);
        assertThat(accounts.lastLogin.get(steve.id())).isEqualTo(NOW);
    }

    @Test
    void aWrongPasswordCostsAnAttemptUntilNoneAreLeft() {
        Account steve = register(hashing.hash(Password.of("hunter22")));
        waitForLogin(steve, 2);
        LogIn logIn = logIn();

        assertThat(logIn(logIn, "nope")).isEqualTo(new LoginResult.Wrong(1));
        assertThat(logIn(logIn, "nope")).isEqualTo(new LoginResult.TooManyAttempts());
        assertThat(logIn(logIn, "hunter22")).isEqualTo(new LoginResult.TooManyAttempts());
        assertThat(sessions.state(connection)).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    void anImportedHashIsReplacedWithTheCurrentAlgorithmOnLogin() {
        Account steve = register(AUTHME_HUNTER22);
        waitForLogin(steve, 3);

        assertThat(logIn(logIn(), "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(HashFormats.recognise(Objects.requireNonNull(
                        accounts.findById(steve.id()).orElseThrow().password())))
                .contains(HashAlgorithm.BCRYPT);
    }

    @Test
    void aPasswordTheRulesNoLongerAcceptMustBeReplaced() {
        Account steve = register(hashing.hash(Password.of("abc")));
        waitForLogin(steve, 3);
        PasswordPolicy longer = new PasswordPolicy(6, 64, Pattern.compile("(?s).*"), Set.of(), 0);
        LogIn logIn = new LogIn(
                accounts,
                hashing,
                lane,
                sessions,
                Clock.fixed(NOW, ZoneOffset.UTC),
                (a, h) -> Optional.empty(),
                () -> longer);

        assertThat(logIn(logIn, "abc")).isEqualTo(new LoginResult.MustReplace(new PasswordVerdict.TooShort(6)));
        assertThat(sessions.state(connection)).containsInstanceOf(AuthState.Replacing.class);
    }

    @Test
    void tooManyFailuresFromOneAddressBanIt() {
        Account steve = register(hashing.hash(Password.of("hunter22")));
        waitForLogin(steve, 5);
        LogIn logIn = new LogIn(
                accounts,
                hashing,
                lane,
                sessions,
                Clock.fixed(NOW, ZoneOffset.UTC),
                (address, host) -> Optional.of(Duration.ofMinutes(10)));

        assertThat(logIn(logIn, "nope")).isEqualTo(new LoginResult.Banned(Duration.ofMinutes(10)));
    }

    @Test
    void aConnectionThatIsNotWaitingCannotLogIn() {
        Account steve = register(hashing.hash(Password.of("hunter22")));

        assertThat(logIn(logIn(), "hunter22")).isEqualTo(new LoginResult.Gone());

        sessions.open(connection, new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(logIn(logIn(), "hunter22")).isEqualTo(new LoginResult.NotRegistered());

        sessions.open(
                connection, new AuthState.Authenticated(steve, com.uxplima.auth.domain.state.AuthMethod.PASSWORD));
        assertThat(logIn(logIn(), "hunter22")).isEqualTo(new LoginResult.AlreadyLoggedIn());
    }

    @Test
    void aDeletedAccountIsNotRegistered() {
        Account steve = register(hashing.hash(Password.of("hunter22")));
        waitForLogin(steve, 3);
        accounts.delete(steve.id());

        assertThat(logIn(logIn(), "hunter22")).isEqualTo(new LoginResult.NotRegistered());
    }

    /** Accounts in a map, enough for the login path. */
    private static final class Accounts implements AccountRepository {

        private final Map<UUID, Account> byId = new HashMap<>();
        private final Map<UUID, Instant> lastLogin = new HashMap<>();

        @Override
        public Optional<Account> findByName(PlayerName name) {
            return byId.values().stream()
                    .filter(a -> a.name().key().equals(name.key()))
                    .findFirst();
        }

        @Override
        public Optional<Account> findById(UUID id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public Insert insert(Account account) {
            byId.put(account.id(), account);
            return new Inserted();
        }

        @Override
        public void update(Account account) {
            byId.put(account.id(), account);
        }

        @Override
        public void recordLogin(UUID id, Instant at, String address) {
            lastLogin.put(id, at);
        }

        @Override
        public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
            Account account = byId.get(id);
            if (account == null || !Objects.equals(account.password(), expected)) {
                return false;
            }
            byId.put(id, account.withPassword(fresh));
            return true;
        }

        @Override
        public int countRegisteredFrom(String address, Instant since) {
            return 0;
        }

        @Override
        public Optional<Account> findByPremiumId(UUID premiumId) {
            return Optional.empty();
        }

        @Override
        public Insert rename(UUID id, PlayerName to, Instant at) {
            return new Inserted();
        }

        @Override
        public Optional<PlayerName> renamedFrom(PlayerName old) {
            return Optional.empty();
        }

        @Override
        public Optional<Renamed> untoldRename(UUID id) {
            return Optional.empty();
        }

        @Override
        public void markRenameTold(UUID id) {}

        @Override
        public void delete(UUID id) {
            byId.remove(id);
        }

        @Override
        public void moveId(UUID from, UUID to) {}
    }
}
