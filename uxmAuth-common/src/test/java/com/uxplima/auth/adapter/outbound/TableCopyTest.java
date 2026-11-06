package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableCopyTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final WebsiteTable OFF = new WebsiteTable(
            false,
            "authme",
            WebsiteTable.Hash.SHA256,
            new WebsiteTable.Columns("username", "realname", "password", "ip", "lastlogin", "regdate", "regip"));

    @TempDir
    Path folder;

    @Test
    void aCopyThatFailsHalfwayLeavesTheTargetEmptyAgain() {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Database source = Backend.SQLITE.open(folder);
                TableCopy copy = TableCopy.open(source, new PluginSettings.Storage(url, "", "", 2), OFF, "test");
                Database target = Database.builder().jdbcUrl(url).build()) {
            new SqlAccountRepository(source)
                    .insert(new Account(
                            UUID.randomUUID(),
                            new PlayerName("Steve"),
                            AccountKind.CRACKED,
                            null,
                            new PasswordHash("$SHA$a$1"),
                            NOW,
                            "203.0.113.7",
                            null,
                            null));
            // The last table cannot be written, so the copy fails after the accounts went across.
            new Sql(target).execute("DROP TABLE uxmauth_import_account");

            assertThatThrownBy(copy::copy).isInstanceOf(StorageException.class).hasMessageContaining("emptied again");

            assertThat(new Sql(target)
                            .queryFirst("SELECT COUNT(*) FROM uxmauth_account", statement -> {}, row -> row.getLong(1)))
                    .contains(0L);
        }
    }
}
