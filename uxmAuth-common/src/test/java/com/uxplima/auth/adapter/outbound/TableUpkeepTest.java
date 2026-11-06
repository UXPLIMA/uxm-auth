package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TableUpkeepTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration FORGIVE = Duration.ofHours(24);

    @TempDir
    Path folder;

    @ParameterizedTest
    @EnumSource(Backend.class)
    void forgivenAddressesAndEndedSessionsGoAndTheRestStays(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAddressTallies tallies = new SqlAddressTallies(database);
            SqlRememberedSessions sessions = new SqlRememberedSessions(database, Clock.fixed(NOW, ZoneOffset.UTC));
            Instant longAgo = NOW.minus(Duration.ofDays(3));
            tallies.change("198.51.100.1", tally -> new AddressTally(2, 0, null, longAgo));
            tallies.change("198.51.100.2", tally -> new AddressTally(1, 0, null, NOW.minusSeconds(60)));
            tallies.change("198.51.100.3", tally -> new AddressTally(0, 1, NOW.plus(Duration.ofDays(2)), longAgo));
            UUID gone = UUID.randomUUID();
            UUID kept = UUID.randomUUID();
            sessions.remember(gone, "198.51.100.1", NOW.minusSeconds(1));
            sessions.remember(kept, "198.51.100.2", NOW.plus(Duration.ofDays(1)));
            List<String> console = new ArrayList<>();

            new TableUpkeep(tallies, sessions, () -> FORGIVE, Clock.fixed(NOW, ZoneOffset.UTC), console::add).run();

            assertThat(tallies.find("198.51.100.1")).isEmpty();
            assertThat(tallies.find("198.51.100.2")).isPresent();
            assertThat(tallies.find("198.51.100.3")).isPresent();
            assertThat(sessions.holds(kept, "198.51.100.2", NOW)).isTrue();
            assertThat(sessions.dropExpired(NOW)).isZero();
            assertThat(console).isEmpty();
        }
    }
}
