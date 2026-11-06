package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class WebsiteAccountsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final PlayerName STEVE = new PlayerName("Steve");
    private static final PasswordHash OWN = new PasswordHash("$SHA$aaaa$own");
    private static final PasswordHash THEIRS = new PasswordHash("$SHA$bbbb$theirs");
    private static final WebsiteTable WEBSITE = new WebsiteTable(
            true,
            "authme",
            WebsiteTable.Hash.SHA256,
            new WebsiteTable.Columns("username", "realname", "password", "ip", "lastlogin", "regdate", "regip"));

    @TempDir
    Path folder;

    private static WebsiteAccounts over(Database database) {
        return WebsiteAccounts.over(
                new SqlAccountRepository(database), database, WEBSITE, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Account steve(UUID id) {
        return new Account(id, STEVE, AccountKind.CRACKED, null, OWN, NOW, "203.0.113.7", null, null);
    }

    private static PasswordHash passwordOf(AccountRepository accounts) {
        return Objects.requireNonNull(accounts.findByName(STEVE).orElseThrow().password());
    }

    /** The website deletes the row and somebody registers the name there again, later. */
    private static void registeredAgainOnTheWebsite(Database database, long at) {
        Sql sql = new Sql(database);
        sql.update("DELETE FROM authme WHERE username = ?", statement -> statement.setString(1, "steve"));
        sql.update("INSERT INTO authme (username, realname, password, regdate) VALUES (?, ?, ?, ?)", statement -> {
            statement.setString(1, "steve");
            statement.setString(2, "Steve");
            statement.setString(3, THEIRS.encoded());
            statement.setLong(4, at);
        });
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aRowRegisteredAgainOnTheWebsiteOpensNothing(Backend backend) {
        try (Database database = backend.open(folder)) {
            WebsiteAccounts accounts = over(database);
            accounts.insert(steve(UUID.randomUUID()));

            registeredAgainOnTheWebsite(database, NOW.plusSeconds(60).toEpochMilli());

            assertThat(passwordOf(accounts)).isEqualTo(OWN);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aRowThatWentEndsTheLinkBeforeAnotherComes(Backend backend) {
        try (Database database = backend.open(folder)) {
            WebsiteAccounts accounts = over(database);
            accounts.insert(steve(UUID.randomUUID()));
            new Sql(database)
                    .update("DELETE FROM authme WHERE username = ?", statement -> statement.setString(1, "steve"));
            assertThat(passwordOf(accounts)).isEqualTo(OWN);

            // The same date as the account's own row had, so only the ended link keeps it out.
            registeredAgainOnTheWebsite(database, NOW.toEpochMilli());

            assertThat(passwordOf(accounts)).isEqualTo(OWN);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aPasswordChangedOnTheWebsiteStillOpensTheAccount(Backend backend) {
        try (Database database = backend.open(folder)) {
            WebsiteAccounts accounts = over(database);
            accounts.insert(steve(UUID.randomUUID()));

            new Sql(database).update("UPDATE authme SET password = ? WHERE username = ?", statement -> {
                statement.setString(1, THEIRS.encoded());
                statement.setString(2, "steve");
            });

            assertThat(passwordOf(accounts)).isEqualTo(THEIRS);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aLinkFromBeforeBindsTheRowItFindsFirst(Backend backend) {
        try (Database database = backend.open(folder)) {
            WebsiteAccounts accounts = over(database);
            accounts.insert(steve(UUID.randomUUID()));
            Sql sql = new Sql(database);
            sql.update("UPDATE uxmauth_website_link SET row_mark = NULL", statement -> {});
            sql.update("UPDATE authme SET password = ? WHERE username = ?", statement -> {
                statement.setString(1, THEIRS.encoded());
                statement.setString(2, "steve");
            });
            assertThat(passwordOf(accounts)).isEqualTo(THEIRS);

            registeredAgainOnTheWebsite(database, NOW.plusSeconds(60).toEpochMilli());

            assertThat(passwordOf(accounts)).isEqualTo(OWN);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aTableWithNeitherNumbersNorDatesIsToldApartByThePassword(Backend backend) {
        try (Database database = backend.open(folder)) {
            new Sql(database)
                    .execute("CREATE TABLE authme (username VARCHAR(255) NOT NULL UNIQUE,"
                            + " realname VARCHAR(255) NOT NULL, password VARCHAR(255) NOT NULL, ip VARCHAR(40),"
                            + " lastlogin BIGINT, regdate BIGINT DEFAULT 0 NOT NULL, regip VARCHAR(40))");
            WebsiteAccounts accounts = over(database);
            Account plain = steve(UUID.randomUUID());
            accounts.insert(plain);
            new Sql(database).update("UPDATE authme SET regdate = 0", statement -> {});
            accounts.update(plain);

            registeredAgainOnTheWebsite(database, 0);

            assertThat(passwordOf(accounts)).isEqualTo(OWN);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void removingTheAccountLeavesARowRegisteredAgainAlone(Backend backend) {
        try (Database database = backend.open(folder)) {
            WebsiteAccounts accounts = over(database);
            UUID id = UUID.randomUUID();
            accounts.insert(steve(id));
            registeredAgainOnTheWebsite(database, NOW.plusSeconds(60).toEpochMilli());

            accounts.delete(id);

            assertThat(new Sql(database)
                            .queryFirst(
                                    "SELECT password FROM authme WHERE username = ?",
                                    statement -> statement.setString(1, "steve"),
                                    row -> row.getString(1)))
                    .contains(THEIRS.encoded());
        }
    }
}
