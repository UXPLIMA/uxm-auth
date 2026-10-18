package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Confirmed addresses and mailed codes in the table every server of a network reads. */
final class SqlEmailsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final EmailAddress STEVE =
            EmailAddress.parse("Steve@example.net").orElseThrow();

    @TempDir
    Path folder;

    private Database database;
    private SqlEmails emails;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "email-test");
        emails = new SqlEmails(database, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        database.close();
    }

    private static Emails.Code code(String hash, int attempts) {
        return new Emails.Code(STEVE, new PasswordHash(hash), NOW.plusSeconds(900), attempts, NOW);
    }

    @Test
    @DisplayName("a confirmed address is kept as typed, replaced by the next, and removed")
    void anAddressIsKept() {
        UUID account = UUID.randomUUID();

        assertThat(emails.address(account)).isEmpty();
        emails.setAddress(account, STEVE);
        assertThat(emails.address(account)).contains(STEVE);
        EmailAddress other = EmailAddress.parse("other@example.org").orElseThrow();
        emails.setAddress(account, other);
        assertThat(emails.address(account)).contains(other);
        emails.removeAddress(account);
        assertThat(emails.address(account)).isEmpty();
    }

    @Test
    @DisplayName("a code is written, read back whole, changed, kept apart by purpose, and taken away")
    void aCodeIsChanged() {
        UUID account = UUID.randomUUID();

        assertThat(emails.changeCode(account, Emails.Purpose.VERIFY, before -> {
                    assertThat(before).isEmpty();
                    return Optional.of(code("$hash$a", 5));
                }))
                .contains(code("$hash$a", 5));
        assertThat(emails.code(account, Emails.Purpose.VERIFY)).contains(code("$hash$a", 5));
        assertThat(emails.code(account, Emails.Purpose.RECOVERY)).isEmpty();

        emails.changeCode(account, Emails.Purpose.VERIFY, before -> before.map(Emails.Code::spent));
        assertThat(emails.code(account, Emails.Purpose.VERIFY)).contains(code("$hash$a", 4));

        emails.changeCode(account, Emails.Purpose.VERIFY, before -> Optional.empty());
        assertThat(emails.code(account, Emails.Purpose.VERIFY)).isEmpty();
    }

    @Test
    @DisplayName("on a database that reads committed rows, of many servers spending one try together each spends one")
    void triesAreSpentOneByOne() throws Exception {
        Database h2 = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:h2:" + folder.resolve("server").toAbsolutePath(), "sa", "", 8),
                "email-test-h2");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            SqlEmails shared = new SqlEmails(h2, Clock.fixed(NOW, ZoneOffset.UTC));
            UUID account = UUID.randomUUID();
            shared.changeCode(account, Emails.Purpose.LOGIN, before -> Optional.of(code("$hash$b", 10)));
            List<CompletableFuture<Optional<Emails.Code>>> spent = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                spent.add(CompletableFuture.supplyAsync(
                        () -> shared.changeCode(
                                account,
                                Emails.Purpose.LOGIN,
                                before -> before.map(code -> code.attemptsLeft() > 0 ? code.spent() : code)),
                        pool));
            }
            spent.forEach(CompletableFuture::join);

            assertThat(shared.code(account, Emails.Purpose.LOGIN))
                    .hasValueSatisfying(code -> assertThat(code.attemptsLeft()).isZero());
        } finally {
            pool.shutdownNow();
            h2.close();
        }
    }
}
