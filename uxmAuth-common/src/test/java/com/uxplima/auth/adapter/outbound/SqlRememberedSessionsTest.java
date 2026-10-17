package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Remembered sessions and each account's choice, kept where every member of a network reads them. */
final class SqlRememberedSessionsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @TempDir
    Path folder;

    private Database database;
    private SqlRememberedSessions sessions;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "session-test");
        sessions = new SqlRememberedSessions(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a session holds from its own address until it runs out, and a later one replaces it")
    void aSessionHoldsFromItsAddressUntilItRunsOut() {
        UUID steve = UUID.randomUUID();
        sessions.remember(steve, "203.0.113.7", NOW.plusSeconds(60));

        assertThat(sessions.holds(steve, "203.0.113.7", NOW)).isTrue();
        assertThat(sessions.holds(steve, "203.0.113.8", NOW)).isFalse();
        assertThat(sessions.holds(UUID.randomUUID(), "203.0.113.7", NOW)).isFalse();
        assertThat(sessions.holds(steve, "203.0.113.7", NOW.plusSeconds(61))).isFalse();

        sessions.remember(steve, "203.0.113.7", NOW.plusSeconds(600));
        assertThat(sessions.holds(steve, "203.0.113.7", NOW.plusSeconds(300))).isTrue();
    }

    @Test
    @DisplayName("forgetting an account ends every session it has, from every address")
    void forgettingEndsEverySession() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        sessions.remember(steve, "203.0.113.7", NOW.plusSeconds(60));
        sessions.remember(steve, "198.51.100.2", NOW.plusSeconds(60));
        sessions.remember(alex, "203.0.113.7", NOW.plusSeconds(60));

        sessions.forget(steve);

        assertThat(sessions.holds(steve, "203.0.113.7", NOW)).isFalse();
        assertThat(sessions.holds(steve, "198.51.100.2", NOW)).isFalse();
        assertThat(sessions.holds(alex, "203.0.113.7", NOW)).isTrue();
    }

    @Test
    @DisplayName("an account's choice is unknown until made, and the latest one stands")
    void theChoiceIsKept() {
        UUID steve = UUID.randomUUID();
        assertThat(sessions.choice(steve)).isEmpty();

        sessions.choose(steve, true);
        assertThat(sessions.choice(steve)).contains(true);
        sessions.choose(steve, false);
        assertThat(sessions.choice(steve)).contains(false);
    }
}
