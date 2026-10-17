package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.UUID;

import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The last server of each account, kept in the shared database so every proxy reads the same one. */
final class SqlLastServersTest {

    @TempDir
    Path folder;

    private Database database;
    private SqlLastServers servers;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "last-test");
        servers = new SqlLastServers(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("an account with no last server has none, and the latest one remembered is the one read")
    void theLatestServerIsRead() {
        UUID steve = UUID.randomUUID();
        assertThat(servers.of(steve)).isEmpty();

        servers.remember(steve, "survival");
        servers.remember(steve, "skyblock");

        assertThat(servers.of(steve)).contains("skyblock");
        assertThat(servers.of(UUID.randomUUID())).isEmpty();
    }
}
