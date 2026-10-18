package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The tally of each address, kept where every member of a network reads it, changed one writer at a time. */
final class SqlAddressTalliesTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

    @TempDir
    Path folder;

    private Database database;
    private SqlAddressTallies tallies;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 2), "address-test");
        tallies = new SqlAddressTallies(database);
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("a tally is written whole and read back whole, and an address never seen has none")
    void aTallyIsKeptWhole() {
        assertThat(tallies.find("203.0.113.7")).isEmpty();

        AddressTally written =
                tallies.change("203.0.113.7", tally -> new AddressTally(2, 1, NOW.plusSeconds(300), NOW));
        AddressTally cleared = tallies.change("198.51.100.2", tally -> new AddressTally(1, 0, null, NOW));

        assertThat(tallies.find("203.0.113.7")).contains(written);
        assertThat(written).isEqualTo(new AddressTally(2, 1, NOW.plusSeconds(300), NOW));
        assertThat(tallies.find("198.51.100.2")).contains(cleared);
        assertThat(cleared.bannedUntil()).isNull();
    }

    @Test
    @DisplayName("a change starts from what is stored, or from a clean tally the first time")
    void aChangeStartsFromWhatIsStored() {
        List<AddressTally> seen = new ArrayList<>();
        tallies.change("203.0.113.7", tally -> {
            seen.add(tally);
            return new AddressTally(tally.failures() + 1, 0, null, NOW);
        });
        tallies.change("203.0.113.7", tally -> {
            seen.add(tally);
            return new AddressTally(tally.failures() + 1, 0, null, NOW);
        });

        assertThat(seen).containsExactly(AddressTally.clean(), new AddressTally(1, 0, null, NOW));
    }

    @Test
    @DisplayName("changes sent together from many threads are all counted")
    void changesTogetherAreAllCounted() throws Exception {
        assertThat(countedTogether(tallies)).isEqualTo(40);
    }

    @Test
    @DisplayName(
            "changes sent together are all counted on a database that reads committed rows, as MySQL and PostgreSQL do")
    void changesTogetherAreAllCountedOnAServerDatabase() throws Exception {
        Database h2 = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:h2:" + folder.resolve("server").toAbsolutePath(), "sa", "", 8),
                "address-test-h2");
        try {
            assertThat(countedTogether(new SqlAddressTallies(h2))).isEqualTo(40);
        } finally {
            h2.close();
        }
    }

    private static int countedTogether(SqlAddressTallies tallies) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<CompletableFuture<AddressTally>> sent = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                sent.add(CompletableFuture.supplyAsync(
                        () -> tallies.change(
                                "203.0.113.7", tally -> new AddressTally(tally.failures() + 1, 0, null, NOW)),
                        pool));
            }
            CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new)).join();
        } finally {
            pool.shutdown();
        }
        return tallies.find("203.0.113.7").orElseThrow().failures();
    }

    @Test
    @DisplayName("clearing an address forgets its tally")
    void clearingForgets() {
        tallies.change("203.0.113.7", tally -> new AddressTally(1, 0, null, NOW));

        tallies.clear("203.0.113.7");

        assertThat(tallies.find("203.0.113.7")).isEmpty();
    }
}
