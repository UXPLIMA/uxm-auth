package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SqlAccountTalliesTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final BanSteps STEPS = new BanSteps(List.of(Duration.ofMinutes(1)), 2, Duration.ofHours(24));

    @TempDir
    static Path folder;

    static Stream<String> databases() {
        return Stream.of(
                "jdbc:sqlite:" + folder.resolve("auth.db"),
                "jdbc:h2:" + folder.resolve("auth-h2").toAbsolutePath());
    }

    @ParameterizedTest
    @MethodSource("databases")
    void anAccountsTallyIsKeptAndForgotten(String url) {
        try (Database database = AuthDatabase.open(new PluginSettings.Storage(url, "", "", 2), "tallies-test")) {
            SqlAccountTallies tallies = new SqlAccountTallies(database);
            UUID account = UUID.randomUUID();

            tallies.change(account, tally -> tally.failedAt(NOW, STEPS));
            AddressTally locked = tallies.change(account, tally -> tally.failedAt(NOW, STEPS));

            assertThat(locked.bannedAt(NOW)).isTrue();
            assertThat(tallies.find(account)).contains(locked);
            tallies.clear(account);
            assertThat(tallies.find(account)).isEmpty();
        }
    }
}
