package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every login an account made, newest first, kept for as long as the operator says. */
final class SqlLoginHistoryTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");

    @TempDir
    Path folder;

    private Database database;
    private SqlLoginHistory history;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "history-test");
        history = new SqlLoginHistory(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a login is kept with its time, address and way in, and read back newest first, as many as asked")
    void loginsAreKept() {
        UUID steve = UUID.randomUUID();
        history.record(steve, new LoginHistory.Entry(NOW.minusSeconds(60), "203.0.113.7", AuthMethod.PASSWORD));
        history.record(steve, new LoginHistory.Entry(NOW, "198.51.100.2", AuthMethod.SESSION));
        history.record(UUID.randomUUID(), new LoginHistory.Entry(NOW, "203.0.113.7", AuthMethod.PREMIUM));

        assertThat(history.recent(steve, 10))
                .containsExactly(
                        new LoginHistory.Entry(NOW, "198.51.100.2", AuthMethod.SESSION),
                        new LoginHistory.Entry(NOW.minusSeconds(60), "203.0.113.7", AuthMethod.PASSWORD));
        assertThat(history.recent(steve, 1)).hasSize(1);
        assertThat(history.count(steve)).isEqualTo(2);
    }

    @Test
    @DisplayName("the accounts seen on an address are found, an IPv6 home by its /64, and the address's own key")
    void accountsOnAnAddress() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        history.record(steve, new LoginHistory.Entry(NOW, "2001:db8:1:2::10", AuthMethod.PASSWORD));
        history.record(alex, new LoginHistory.Entry(NOW, "2001:db8:1:2::99", AuthMethod.PASSWORD));
        history.record(alex, new LoginHistory.Entry(NOW.plusSeconds(1), "2001:db8:1:2::99", AuthMethod.SESSION));
        history.record(UUID.randomUUID(), new LoginHistory.Entry(NOW, "2001:db8:9:9::1", AuthMethod.PASSWORD));

        assertThat(history.accountsOn("2001:db8:1:2::1", 10)).containsExactlyInAnyOrder(steve, alex);
        assertThat(history.accountsOn("2001:db8:1:2::1", 1)).hasSize(1);
    }

    @Test
    @DisplayName("pruning drops what is older than the window and keeps the rest")
    void pruneKeepsTheWindow() {
        UUID steve = UUID.randomUUID();
        history.record(steve, new LoginHistory.Entry(NOW.minusSeconds(3600), "203.0.113.7", AuthMethod.PASSWORD));
        history.record(steve, new LoginHistory.Entry(NOW, "203.0.113.7", AuthMethod.PASSWORD));

        assertThat(history.prune(NOW.minusSeconds(60))).isEqualTo(1);
        assertThat(history.recent(steve, 10)).extracting(LoginHistory.Entry::at).containsExactly(NOW);
    }
}
