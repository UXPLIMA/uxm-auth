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
        assertThat(repository.countRegisteredFrom("10.0.0.2")).isZero();
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
        assertThat(repository.countRegisteredFrom("10.0.0.9")).isEqualTo(1);
    }

    @Test
    @DisplayName("registrations are counted by the address they came from")
    void countsRegistrationsFromAnAddress() {
        repository.insert(cracked("Alex", "10.0.0.1"));
        repository.insert(cracked("Steve", "10.0.0.1"));
        repository.insert(cracked("Notch", "10.0.0.2"));

        assertThat(repository.countRegisteredFrom("10.0.0.1")).isEqualTo(2);
        assertThat(repository.countRegisteredFrom("10.0.0.2")).isEqualTo(1);
        assertThat(repository.countRegisteredFrom("10.0.0.3")).isZero();
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
