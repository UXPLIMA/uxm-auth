package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/** The account table on an SQLite file, the backend a server gets when the operator names none. */
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class SqlAccountRepositoryTest {

    private static final Instant REGISTERED = Instant.ofEpochMilli(1_760_000_000_000L);

    @TempDir
    Path folder;

    private Database database;

    private SqlAccountRepository repository;

    @BeforeEach
    void open() {
        database = AuthSchema.migrate(Database.builder()
                .sqlite(folder.resolve("auth.db"))
                .poolName("uxmauth-test")
                .build());
        repository = new SqlAccountRepository(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a name is found in any case and comes back as it was registered")
    void findsByNameInAnyCase() {
        Account notch = cracked("Notch", "10.0.0.1");
        repository.insert(notch);

        assertThat(repository.findByName(new PlayerName("NOTCH")))
                .hasValueSatisfying(found -> assertThat(found.name().value()).isEqualTo("Notch"));
        assertThat(repository.findById(notch.id())).contains(notch);
    }

    @Test
    @DisplayName("a second account under the same name in another case is refused, naming the first")
    void secondInsertOfTheSameKeyIsNameTaken() {
        repository.insert(cracked("Notch", "10.0.0.1"));

        AccountRepository.Insert second = repository.insert(cracked("notch", "10.0.0.2"));

        assertThat(second).isEqualTo(new AccountRepository.NameTaken(new PlayerName("Notch")));
        assertThat(repository.countRegisteredFrom("10.0.0.2", Instant.EPOCH)).isZero();
    }

    @Test
    @DisplayName("eight registrations of one name at the same moment leave exactly one account")
    void concurrentInsertsOfOneNameLeaveOneRow() throws InterruptedException {
        List<String> casings = List.of("Steve", "steve", "STEVE", "sTeve", "stEve", "steVe", "stevE", "StEvE");
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<AccountRepository.Insert>> results = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(casings.size())) {
            for (String casing : casings) {
                results.add(CompletableFuture.supplyAsync(
                        () -> {
                            awaitQuietly(start);
                            return repository.insert(cracked(casing, "10.0.0.9"));
                        },
                        threads));
            }
            start.countDown();
            List<AccountRepository.Insert> answers =
                    results.stream().map(CompletableFuture::join).toList();

            assertThat(answers)
                    .filteredOn(AccountRepository.Inserted.class::isInstance)
                    .hasSize(1);
            assertThat(answers)
                    .filteredOn(AccountRepository.NameTaken.class::isInstance)
                    .hasSize(7);
        }
        assertThat(repository.countRegisteredFrom("10.0.0.9", Instant.EPOCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("registrations are counted by the address they came from")
    void countsRegistrationsFromAnAddress() {
        repository.insert(cracked("Alex", "10.0.0.1"));
        repository.insert(cracked("Steve", "10.0.0.1"));
        repository.insert(cracked("Notch", "10.0.0.2"));

        assertThat(repository.countRegisteredFrom("10.0.0.1", Instant.EPOCH)).isEqualTo(2);
        assertThat(repository.countRegisteredFrom("10.0.0.2", Instant.EPOCH)).isEqualTo(1);
        assertThat(repository.countRegisteredFrom("10.0.0.3", Instant.EPOCH)).isZero();
    }

    @Test
    @DisplayName("only password accounts made inside the window count, and an IPv6 home counts by its /64")
    void countsInsideTheWindowByKindAndPrefix() {
        repository.insert(cracked("Alex", "10.0.0.1"));
        repository.insert(withKind(cracked("Notch", "10.0.0.1"), AccountKind.PREMIUM));
        repository.insert(withKind(cracked("Bedrock", "10.0.0.1"), AccountKind.BEDROCK));
        repository.insert(cracked("Herobrine", "2001:db8:1:2::1"));
        repository.insert(cracked("Jeb", "2001:db8:1:2::99"));

        assertThat(repository.countRegisteredFrom("10.0.0.1", REGISTERED)).isEqualTo(1);
        assertThat(repository.countRegisteredFrom("10.0.0.1", REGISTERED.plusMillis(1)))
                .isZero();
        assertThat(repository.countRegisteredFrom("2001:db8:1:2::5", REGISTERED))
                .isEqualTo(2);
        assertThat(repository.countRegisteredFrom("2001:db8:1:3::5", REGISTERED))
                .isZero();
    }

    private static Account withKind(Account account, AccountKind kind) {
        return new Account(
                account.id(),
                account.name(),
                kind,
                kind == AccountKind.PREMIUM ? UUID.randomUUID() : null,
                account.password(),
                account.registeredAt(),
                account.registrationAddress(),
                null,
                null);
    }

    @Test
    @DisplayName("an update keeps every field, the empty ones included")
    void updateKeepsEveryField() {
        Account bare = new Account(
                UUID.randomUUID(),
                new PlayerName("Jeb_"),
                AccountKind.PREMIUM,
                null,
                null,
                REGISTERED,
                "2001:db8::1",
                null,
                null);
        repository.insert(bare);
        assertThat(repository.findById(bare.id())).contains(bare);

        Account full = new Account(
                bare.id(),
                bare.name(),
                AccountKind.PREMIUM,
                UUID.randomUUID(),
                new PasswordHash("$2b$12$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK"),
                REGISTERED,
                "2001:db8::1",
                REGISTERED.plusSeconds(60),
                "2001:db8::2");
        repository.update(full);

        assertThat(repository.findById(bare.id())).contains(full);
        assertThat(repository.findByName(new PlayerName("jeb_"))).contains(full);
    }

    @Test
    @DisplayName("an account taken while nobody could say who owns the name comes back unverified, until verified")
    void unverifiedRoundTrips() {
        Account taken = cracked("Notch", "10.0.0.1").asUnverified();
        repository.insert(taken);

        assertThat(repository.findById(taken.id()))
                .hasValueSatisfying(found -> assertThat(found.unverified()).isTrue());

        repository.update(taken.asVerified());
        assertThat(repository.findById(taken.id()))
                .hasValueSatisfying(found -> assertThat(found.unverified()).isFalse());
    }

    @Test
    @DisplayName("a premium account is found by its Mojang id, whatever name it holds now")
    void findsByPremiumId() {
        UUID mojang = UUID.randomUUID();
        Account premium = new Account(
                UUID.randomUUID(),
                new PlayerName("Jeb_"),
                AccountKind.PREMIUM,
                mojang,
                null,
                REGISTERED,
                "10.0.0.1",
                null,
                null);
        repository.insert(premium);
        repository.insert(cracked("Steve", "10.0.0.1"));

        assertThat(repository.findByPremiumId(mojang)).contains(premium);
        assertThat(repository.findByPremiumId(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("a rename moves the name and keeps the id, frees the old name, and remembers where it went")
    void aRenameMovesTheName() {
        Account steve = cracked("Notch", "10.0.0.1");
        repository.insert(steve);

        assertThat(repository.rename(steve.id(), new PlayerName("Notch_"), REGISTERED))
                .isEqualTo(new AccountRepository.Inserted());

        assertThat(repository.findById(steve.id()))
                .hasValueSatisfying(found -> assertThat(found.name().value()).isEqualTo("Notch_"));
        assertThat(repository.findByName(new PlayerName("notch"))).isEmpty();
        assertThat(repository.renamedFrom(new PlayerName("NOTCH"))).contains(new PlayerName("Notch_"));
        assertThat(repository.untoldRename(steve.id()))
                .contains(new AccountRepository.Renamed(new PlayerName("Notch"), new PlayerName("Notch_")));

        repository.markRenameTold(steve.id());
        assertThat(repository.untoldRename(steve.id())).isEmpty();
        assertThat(repository.renamedFrom(new PlayerName("Notch"))).contains(new PlayerName("Notch_"));
    }

    @Test
    @DisplayName("a rename to a name somebody holds is refused, and nothing moves")
    void aRenameToATakenNameIsRefused() {
        Account steve = cracked("Notch", "10.0.0.1");
        repository.insert(steve);
        repository.insert(cracked("Notch_", "10.0.0.2"));

        assertThat(repository.rename(steve.id(), new PlayerName("NOTCH_"), REGISTERED))
                .isEqualTo(new AccountRepository.NameTaken(new PlayerName("Notch_")));

        assertThat(repository.findById(steve.id()))
                .hasValueSatisfying(found -> assertThat(found.name().value()).isEqualTo("Notch"));
        assertThat(repository.renamedFrom(new PlayerName("Notch"))).isEmpty();
    }

    @Test
    @DisplayName("an account can be deleted, and an id moved to another")
    void deletesAndMovesIds() {
        Account steve = cracked("Steve", "10.0.0.1");
        Account alex = cracked("Alex", "10.0.0.1");
        repository.insert(steve);
        repository.insert(alex);
        UUID fresh = UUID.randomUUID();

        repository.delete(steve.id());
        repository.moveId(alex.id(), fresh);

        assertThat(repository.findById(steve.id())).isEmpty();
        assertThat(repository.findById(alex.id())).isEmpty();
        assertThat(repository.findById(fresh))
                .hasValueSatisfying(found -> assertThat(found.name().value()).isEqualTo("Alex"));
    }

    @Test
    @DisplayName("a deleted account takes its remembered sessions with it, and a moved one leaves them behind")
    void sessionsGoWithTheAccount() {
        Account steve = cracked("Steve", "10.0.0.1");
        Account alex = cracked("Alex", "10.0.0.1");
        repository.insert(steve);
        repository.insert(alex);
        SqlRememberedSessions sessions = new SqlRememberedSessions(database);
        java.time.Instant now = java.time.Instant.parse("2026-10-18T10:00:00Z");
        sessions.remember(steve.id(), "10.0.0.1", now.plusSeconds(60));
        sessions.remember(alex.id(), "10.0.0.1", now.plusSeconds(60));
        sessions.choose(alex.id(), true);
        UUID fresh = UUID.randomUUID();

        repository.delete(steve.id());
        repository.moveId(alex.id(), fresh);

        assertThat(sessions.holds(steve.id(), "10.0.0.1", now)).isFalse();
        assertThat(sessions.holds(alex.id(), "10.0.0.1", now)).isFalse();
        assertThat(sessions.holds(fresh, "10.0.0.1", now)).isFalse();
        assertThat(sessions.choice(fresh)).contains(true);
    }

    /** Everything a store keeps under one account id, read back the way each store reads it. */
    private record Kept(boolean app, int backupCodes, boolean address, boolean code, int logins, boolean lastServer) {}

    private Kept keep(UUID id) {
        java.time.Clock clock = java.time.Clock.fixed(REGISTERED, java.time.ZoneOffset.UTC);
        SqlFactors factors = new SqlFactors(database, SecretSeal.fromPassphrase("lab"), clock);
        SqlEmails emails = new SqlEmails(database, clock);
        SqlLoginHistory history = new SqlLoginHistory(database);
        SqlLastServers servers = new SqlLastServers(database, clock);
        com.uxplima.auth.domain.mail.EmailAddress address = com.uxplima.auth.domain.mail.EmailAddress.parse(
                        "steve@example.net")
                .orElseThrow();
        factors.setApp(id, new com.uxplima.auth.domain.factor.TotpSecret(new byte[20]));
        factors.setBackupCodes(id, java.util.List.of(new com.uxplima.auth.domain.hash.PasswordHash("$x$a")));
        emails.setAddress(id, address);
        var unused = emails.changeCode(
                id,
                com.uxplima.auth.application.port.Emails.Purpose.LOGIN,
                none -> java.util.Optional.of(new com.uxplima.auth.application.port.Emails.Code(
                        address,
                        new com.uxplima.auth.domain.hash.PasswordHash("$x$c"),
                        REGISTERED.plusSeconds(600),
                        3,
                        REGISTERED)));
        history.record(
                id,
                new com.uxplima.auth.application.port.LoginHistory.Entry(
                        REGISTERED, "10.0.0.1", com.uxplima.auth.domain.state.AuthMethod.PASSWORD));
        servers.remember(id, "lobby");
        return kept(id);
    }

    private Kept kept(UUID id) {
        java.time.Clock clock = java.time.Clock.fixed(REGISTERED, java.time.ZoneOffset.UTC);
        SqlFactors factors = new SqlFactors(database, SecretSeal.fromPassphrase("lab"), clock);
        SqlEmails emails = new SqlEmails(database, clock);
        return new Kept(
                factors.app(id).isPresent(),
                factors.backupCodes(id).size(),
                emails.address(id).isPresent(),
                emails.code(id, com.uxplima.auth.application.port.Emails.Purpose.LOGIN)
                        .isPresent(),
                new SqlLoginHistory(database).count(id),
                new SqlLastServers(database, clock).of(id).isPresent());
    }

    @Test
    @DisplayName("a deleted account leaves nothing under its id, so a later account under the same id inherits nothing")
    void aDeletedAccountLeavesNothing() {
        Account steve = cracked("Steve", "10.0.0.1");
        repository.insert(steve);
        assertThat(keep(steve.id())).isEqualTo(new Kept(true, 1, true, true, 1, true));

        repository.delete(steve.id());

        assertThat(kept(steve.id())).isEqualTo(new Kept(false, 0, false, false, 0, false));
    }

    @Test
    @DisplayName("a moved id takes its app, codes, address, mailed code, history and last server with it")
    void aMovedIdTakesEverything() {
        Account alex = cracked("Alex", "10.0.0.1");
        repository.insert(alex);
        Kept before = keep(alex.id());
        UUID fresh = UUID.randomUUID();

        repository.moveId(alex.id(), fresh);

        assertThat(kept(fresh)).isEqualTo(before);
        assertThat(kept(alex.id())).isEqualTo(new Kept(false, 0, false, false, 0, false));
    }

    @Test
    @DisplayName("a name nobody registered finds nothing")
    void anUnknownNameFindsNothing() {
        assertThat(repository.findByName(new PlayerName("Nobody"))).isEmpty();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("a login is recorded without touching the rest of the row")
    void recordsALogin() {
        Account notch = cracked("Notch", "10.0.0.1");
        repository.insert(notch);

        repository.recordLogin(notch.id(), REGISTERED.plusSeconds(5), "10.0.0.5");

        assertThat(repository.findById(notch.id())).contains(notch.loggedIn(REGISTERED.plusSeconds(5), "10.0.0.5"));
    }

    @Test
    @DisplayName("a password is replaced only while the row still holds the one expected")
    void replacesAPasswordOnlyFromTheExpectedOne() {
        Account notch = cracked("Notch", "10.0.0.1");
        repository.insert(notch);
        PasswordHash first = new PasswordHash("$2b$04$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK");
        PasswordHash second = new PasswordHash("$2b$04$abcdefghijklmnopqrstuurPnzXnWtHMiY8oTl6PjtlkbaZ.nozZW");

        assertThat(repository.replacePassword(notch.id(), notch.password(), first))
                .isTrue();
        assertThat(repository.replacePassword(notch.id(), notch.password(), second))
                .isFalse();

        assertThat(repository.findById(notch.id()).orElseThrow().password()).isEqualTo(first);
    }

    @Test
    @DisplayName("an account with no password takes its first one only while it still has none")
    void replacesAMissingPassword() {
        Account bare = new Account(
                UUID.randomUUID(),
                new PlayerName("Jeb_"),
                AccountKind.PREMIUM,
                null,
                null,
                REGISTERED,
                "10.0.0.1",
                null,
                null);
        repository.insert(bare);
        PasswordHash first = new PasswordHash("$2b$04$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK");

        assertThat(repository.replacePassword(bare.id(), null, first)).isTrue();
        assertThat(repository.replacePassword(bare.id(), null, first)).isFalse();
    }

    private static Account cracked(String name, String address) {
        return new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$argon2id$v=19$m=1024,t=1,p=1$c29tZXNhbHQxMjM0NTY3OA$aGFzaA"),
                REGISTERED,
                address,
                null,
                null);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
