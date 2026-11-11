package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.Sessions;
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
import com.uxplima.auth.domain.hash.ScryptHasher;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BlysisAccountsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final PlayerName STEVE = new PlayerName("Steve");
    private static final String ADDRESS = "203.0.113.7";
    private static final BlysisSettings SETTINGS = new BlysisSettings(
            true,
            "jdbc:h2:mem:unused",
            "",
            "",
            2,
            Duration.ofSeconds(5),
            "member",
            "{name}@players.example.com",
            "tr",
            "TRY",
            BlysisSettings.Hash.BCRYPT);

    private final Hashing hashing =
            new Hashing(new BcryptHasher(4), List.of(new ScryptHasher(), new AuthMeSha256Hasher()));
    private final List<AutoCloseable> open = new ArrayList<>();

    @TempDir
    Path folder;

    @AfterEach
    void closeEverything() throws Exception {
        for (AutoCloseable closeable : open.reversed()) {
            closeable.close();
        }
    }

    /** This plugin's storage on each backend, beside each Blysis database this run reaches. */
    static Stream<Arguments> stores() {
        return Stream.of(Backend.values())
                .flatMap(backend -> BlysisSite.reachable().stream().map(site -> Arguments.of(backend, site)));
    }

    /** The plugin's database and the website's, and the accounts over both. */
    private record World(Database own, Database site, BlysisAccounts accounts) {
        Sql sql() {
            return new Sql(site);
        }
    }

    private World world(Backend backend, BlysisSite blysis) {
        Database own = backend.open(folder);
        Database site = blysis.open();
        open.add(own);
        open.add(site);
        BlysisAccounts accounts = BlysisAccounts.over(
                new SqlAccountRepository(own), own, site, SETTINGS, Clock.fixed(NOW, ZoneOffset.UTC));
        return new World(own, site, accounts);
    }

    private PasswordHash hash(String password) {
        return hashing.hash(Password.of(password));
    }

    /** A member the website made. */
    private static void member(
            World world, String id, String username, @Nullable String password, boolean banned, boolean deleted) {
        world.sql()
                .update(
                        "INSERT INTO \"User\" (\"id\", \"email\", \"username\", \"password\", \"isBanned\","
                                + " \"isDeleted\", \"createdAt\", \"updatedAt\") VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        statement -> {
                            statement.setString(1, id);
                            statement.setString(2, id + "@example.com");
                            statement.setString(3, username);
                            if (password == null) {
                                statement.setNull(4, Types.VARCHAR);
                            } else {
                                statement.setString(4, password);
                            }
                            statement.setBoolean(5, banned);
                            statement.setBoolean(6, deleted);
                            LocalDateTime made = LocalDateTime.of(2029, 6, 1, 9, 30);
                            statement.setObject(7, made);
                            statement.setObject(8, made);
                        });
    }

    private static void member(World world, String id, String username, String password) {
        member(world, id, username, password, false, false);
    }

    private static Optional<String> column(World world, String id, String column) {
        return world.sql()
                .queryFirst(
                        "SELECT \"" + column + "\" FROM \"User\" WHERE \"id\" = ?",
                        statement -> statement.setString(1, id),
                        row -> Optional.ofNullable(row.getString(1)))
                .flatMap(value -> value);
    }

    private static void set(World world, String id, String column, Object value) {
        world.sql().update("UPDATE \"User\" SET \"" + column + "\" = ? WHERE \"id\" = ?", statement -> {
            statement.setObject(1, value);
            statement.setString(2, id);
        });
    }

    private static int members(World world) {
        return world.sql()
                .queryFirst("SELECT COUNT(*) FROM \"User\"", statement -> {}, row -> row.getInt(1))
                .orElseThrow();
    }

    private static Account cracked(PlayerName name, PasswordHash password) {
        return new Account(
                UuidScheme.offline(name), name, AccountKind.CRACKED, null, password, NOW, ADDRESS, null, null);
    }

    private static PasswordHash passwordOf(AccountRepository accounts, PlayerName name) {
        return Objects.requireNonNull(accounts.findByName(name).orElseThrow().password());
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aMemberJoinsUnderTheirNameInAnyCaseWithTheirPassword(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        String password = hash("hunter22").encoded();
        member(world, "cm1", "Steve", password);

        Account adopted = world.accounts().findByName(new PlayerName("STEVE")).orElseThrow();

        assertThat(adopted.name()).isEqualTo(STEVE);
        assertThat(adopted.id()).isEqualTo(UuidScheme.offline(STEVE));
        assertThat(adopted.kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(adopted.unverified()).isTrue();
        assertThat(adopted.registeredAt()).isEqualTo(Instant.parse("2029-06-01T09:30:00Z"));
        assertThat(Objects.requireNonNull(adopted.password()).encoded()).isEqualTo(password);
        assertThat(world.accounts().closed(adopted.id())).isFalse();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void membersWithoutAPasswordOrDeletedOpenNothingAndHoldTheirName(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", null, false, false);
        member(world, "cm2", "Alex", "", false, true);

        assertThat(world.accounts().findByName(STEVE)).isEmpty();
        assertThat(world.accounts().findByName(new PlayerName("Alex"))).isEmpty();
        assertThat(world.accounts().insert(cracked(STEVE, hash("hunter22"))))
                .isEqualTo(new AccountRepository.NameTaken(STEVE));
        assertThat(new SqlAccountRepository(world.own()).findByName(STEVE)).isEmpty();
        assertThat(column(world, "cm1", "password")).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aBannedMemberIsClosedAndCannotLogIn(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("hunter22").encoded(), true, false);
        Account steve = world.accounts().findByName(STEVE).orElseThrow();

        assertThat(world.accounts().closed(steve.id())).isTrue();
        assertThat(logIn(world.accounts(), steve, "hunter22")).isEqualTo(new LoginResult.Closed());
        assertThat(logIn(world.accounts(), steve, "nope")).isInstanceOf(LoginResult.Wrong.class);

        set(world, "cm1", "isBanned", false);
        assertThat(world.accounts().closed(steve.id())).isFalse();
        assertThat(logIn(world.accounts(), steve, "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aMemberDeletedOnTheWebsiteIsClosed(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("hunter22").encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();

        // What Blysis' own deletion writes.
        set(world, "cm1", "username", "deleted-user-cm1");
        set(world, "cm1", "password", "");
        set(world, "cm1", "isDeleted", true);

        assertThat(world.accounts().closed(steve.id())).isTrue();
        assertThat(logIn(world.accounts(), steve, "hunter22")).isEqualTo(new LoginResult.Closed());
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aRegistrationInGameMakesAMemberInTheMemberRole(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash password = hash("hunter22");

        assertThat(world.accounts().insert(cracked(STEVE, password))).isInstanceOf(AccountRepository.Inserted.class);

        String id = world.sql()
                .queryFirst(
                        "SELECT \"id\" FROM \"User\" WHERE \"username\" = ?",
                        statement -> statement.setString(1, "Steve"),
                        row -> row.getString(1))
                .orElseThrow();
        assertThat(id).startsWith("uxm").hasSize(25);
        assertThat(column(world, id, "email")).contains("steve@players.example.com");
        assertThat(column(world, id, "password")).contains(password.encoded());
        assertThat(column(world, id, "locale")).contains("tr");
        assertThat(column(world, id, "currency")).contains("TRY");
        assertThat(column(world, id, "roleId")).contains(BlysisSite.MEMBER_ROLE);
        LocalDateTime updated = world.sql()
                .queryFirst(
                        "SELECT \"updatedAt\" FROM \"User\" WHERE \"id\" = ?",
                        statement -> statement.setString(1, id),
                        row -> row.getObject(1, LocalDateTime.class))
                .orElseThrow();
        assertThat(updated.toInstant(ZoneOffset.UTC)).isEqualTo(NOW);
        assertThat(world.sql()
                        .queryFirst(
                                "SELECT \"roleId\", \"source\" FROM \"UserRole\" WHERE \"userId\" = ?",
                                statement -> statement.setString(1, id),
                                row -> row.getString(1) + "/" + row.getString(2)))
                .contains(BlysisSite.MEMBER_ROLE + "/" + BlysisMembers.SOURCE);
        assertThat(passwordOf(world.accounts(), STEVE)).isEqualTo(password);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aRegistrationNeverWritesOverAMemberWhateverTheCase(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "sTeVe", null, false, false);

        assertThat(world.accounts().insert(cracked(STEVE, hash("hunter22"))))
                .isEqualTo(new AccountRepository.NameTaken(new PlayerName("sTeVe")));
        assertThat(members(world)).isEqualTo(1);
        assertThat(column(world, "cm1", "password")).isEmpty();
        assertThat(new SqlAccountRepository(world.own()).findByName(STEVE)).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void twoMembersOfOneNameInTwoCasesAreToldApartByTheExactSpelling(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("one").encoded());
        member(world, "cm2", "steve", hash("two").encoded());

        assertThat(world.accounts().findByName(new PlayerName("STEVE"))).isEmpty();
        assertThat(world.accounts()
                        .findByName(new PlayerName("steve"))
                        .orElseThrow()
                        .name()
                        .value())
                .isEqualTo("steve");
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aPasswordChangedInGameReachesTheMemberOnlyFromTheOneItReplaces(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash first = hash("hunter22");
        member(world, "cm1", "Steve", first.encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();
        PasswordHash second = hash("hunter33");
        PasswordHash stale = hash("hunter44");

        assertThat(world.accounts().replacePassword(steve.id(), stale, second)).isFalse();
        assertThat(column(world, "cm1", "password")).contains(first.encoded());

        assertThat(world.accounts().replacePassword(steve.id(), first, second)).isTrue();
        assertThat(column(world, "cm1", "password")).contains(second.encoded());
        assertThat(world.sql()
                        .queryFirst(
                                "SELECT \"updatedAt\" FROM \"User\" WHERE \"id\" = ?",
                                statement -> statement.setString(1, "cm1"),
                                row -> row.getObject(1, LocalDateTime.class))
                        .orElseThrow()
                        .toInstant(ZoneOffset.UTC))
                .isEqualTo(NOW);
        assertThat(new SqlAccountRepository(world.own())
                        .findById(steve.id())
                        .orElseThrow()
                        .password())
                .isEqualTo(second);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aPasswordChangedOnTheWebsiteOpensTheAccount(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("hunter22").encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();

        PasswordHash changed = hash("hunter33");
        set(world, "cm1", "password", changed.encoded());

        assertThat(passwordOf(world.accounts(), STEVE)).isEqualTo(changed);
        assertThat(logIn(world.accounts(), steve, "hunter33")).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void anOlderHashIsMovedToBcryptAtTheMemberAtLogin(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        String authMe = "$SHA$a1b2c3d4e5f6a7b8$db136337234cc82d6d0363b07ec32e8cd4a8fc7900ba6b1b227f1d6e6afc50d4";
        member(world, "cm1", "Steve", authMe);
        Account steve = world.accounts().findByName(STEVE).orElseThrow();

        assertThat(logIn(world.accounts(), steve, "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);

        String moved = column(world, "cm1", "password").orElseThrow();
        assertThat(HashFormats.recognise(new PasswordHash(moved))).contains(HashAlgorithm.BCRYPT);
        assertThat(hashing.verify(Password.of("hunter22"), new PasswordHash(moved)))
                .isInstanceOf(com.uxplima.auth.domain.hash.Verification.Right.class);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aRehashLeavesAPasswordTheWebsiteChangedMeanwhile(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash old = new PasswordHash(
                "$SHA$a1b2c3d4e5f6a7b8$db136337234cc82d6d0363b07ec32e8cd4a8fc7900ba6b1b227f1d6e6afc50d4");
        member(world, "cm1", "Steve", old.encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();
        PasswordHash website = hash("changed-on-the-website");
        set(world, "cm1", "password", website.encoded());

        assertThat(world.accounts().replacePassword(steve.id(), old, hash("hunter22")))
                .isFalse();
        assertThat(column(world, "cm1", "password")).contains(website.encoded());
    }

    @ParameterizedTest
    @MethodSource("stores")
    void removingTheAccountInGameKeepsTheMember(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        String password = hash("hunter22").encoded();
        member(world, "cm1", "Steve", password);
        Account steve = world.accounts().findByName(STEVE).orElseThrow();

        world.accounts().delete(steve.id());

        assertThat(column(world, "cm1", "password")).contains(password);
        assertThat(column(world, "cm1", "username")).contains("Steve");
        // The member still holds a password, so the name is the member's again at the next lookup.
        assertThat(passwordOf(world.accounts(), STEVE).encoded()).isEqualTo(password);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aMemberRenamedOnTheWebsiteKeepsTheirAccountAndIsNotAdoptedTwice(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("hunter22").encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();
        set(world, "cm1", "username", "Steven");
        PasswordHash changed = hash("hunter33");
        set(world, "cm1", "password", changed.encoded());

        assertThat(world.accounts().findById(steve.id()).orElseThrow().password())
                .isEqualTo(changed);
        assertThat(world.accounts().findByName(new PlayerName("Steven"))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aMemberRegisteredOnTheWebsiteUnderAnAccountsNameOpensNothing(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash own = hash("mine");
        new SqlAccountRepository(world.own()).insert(cracked(STEVE, own));
        member(world, "cm1", "Steve", hash("theirs").encoded());

        assertThat(passwordOf(world.accounts(), STEVE)).isEqualTo(own);
        world.accounts().recordLogin(UuidScheme.offline(STEVE), NOW, ADDRESS);
        assertThat(column(world, "cm1", "password")).isNotEqualTo(Optional.of(own.encoded()));
        assertThat(members(world)).isEqualTo(1);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void anAccountFromBeforeBecomesAMemberAtItsNextLogin(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash own = hash("hunter22");
        new SqlAccountRepository(world.own()).insert(cracked(STEVE, own));

        world.accounts().recordLogin(UuidScheme.offline(STEVE), NOW, ADDRESS);

        assertThat(members(world)).isEqualTo(1);
        set(world, firstMemberId(world), "password", hash("hunter33").encoded());
        assertThat(logIn(world.accounts(), world.accounts().findByName(STEVE).orElseThrow(), "hunter33"))
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void anAccountWhoseHashTheMemberHoldsIsLinkedToIt(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        PasswordHash own = hash("hunter22");
        new SqlAccountRepository(world.own()).insert(cracked(STEVE, own));
        member(world, "cm1", "Steve", own.encoded());
        assertThat(passwordOf(world.accounts(), STEVE)).isEqualTo(own);

        PasswordHash changed = hash("hunter33");
        set(world, "cm1", "password", changed.encoded());

        assertThat(passwordOf(world.accounts(), STEVE)).isEqualTo(changed);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aPremiumAccountNeverReadsOrWritesAMember(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        Account premium = new Account(
                UUID.randomUUID(), STEVE, AccountKind.PREMIUM, UUID.randomUUID(), null, NOW, ADDRESS, null, null);

        assertThat(world.accounts().insert(premium)).isInstanceOf(AccountRepository.Inserted.class);
        world.accounts().recordLogin(premium.id(), NOW, ADDRESS);

        assertThat(members(world)).isZero();
        assertThat(world.accounts().closed(premium.id())).isFalse();
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aWebsiteThatDoesNotAnswerFailsTheLookupInsteadOfFreeingTheName(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        member(world, "cm1", "Steve", hash("hunter22").encoded());
        Account steve = world.accounts().findByName(STEVE).orElseThrow();
        world.site().close();

        assertThatThrownBy(() -> world.accounts().findByName(STEVE)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> world.accounts().findByName(new PlayerName("Alex")))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> world.accounts().closed(steve.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> world.accounts().insert(cracked(new PlayerName("Alex"), hash("x"))))
                .isInstanceOf(RuntimeException.class);
        assertThat(new SqlAccountRepository(world.own()).findByName(new PlayerName("Alex")))
                .isEmpty();
        assertThatThrownBy(() -> logIn(world.accounts(), steve, "hunter22")).isInstanceOf(CompletionException.class);
    }

    @ParameterizedTest
    @MethodSource("stores")
    void aWebsiteThatCannotBeReachedStopsTheStart(Backend backend, BlysisSite blysis) {
        World world = world(backend, blysis);
        BlysisSettings unreachable = new BlysisSettings(
                true,
                "jdbc:postgresql://127.0.0.1:1/blysis",
                "uxmauth",
                "secret",
                1,
                Duration.ofSeconds(1),
                "member",
                "{name}@players.example.com",
                "en",
                "USD",
                BlysisSettings.Hash.BCRYPT);

        assertThatThrownBy(() -> BlysisAccounts.open(
                        new SqlAccountRepository(world.own()),
                        world.own(),
                        unreachable,
                        Clock.systemUTC(),
                        "test",
                        warning -> {}))
                .isInstanceOf(StorageException.class)
                .hasMessageNotContaining("secret");
    }

    private static String firstMemberId(World world) {
        return world.sql()
                .queryFirst("SELECT \"id\" FROM \"User\"", statement -> {}, row -> row.getString(1))
                .orElseThrow();
    }

    /** A login of this account through the use case, as a player typing the password does. */
    private LoginResult logIn(AccountRepository accounts, Account account, String typed) {
        Sessions sessions = new Sessions();
        UUID connection = UUID.randomUUID();
        sessions.open(connection, new AuthState.Registered(account, Attempts.fresh(3)));
        try (HashingLane lane = new HashingLane(1, 1, 8)) {
            return new LogIn(accounts, hashing, lane, sessions, Clock.fixed(NOW, ZoneOffset.UTC))
                    .run(connection, Password.of(typed), ADDRESS)
                    .join();
        }
    }
}
