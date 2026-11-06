package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SqlRememberedSessionsTest {

    private static final String HOME = "203.0.113.7";
    private static final String FILTER = "198.51.100.7";

    @TempDir
    static Path folder;

    static Stream<String> databases() {
        return Stream.of(
                "jdbc:sqlite:" + folder.resolve("sessions.db"),
                "jdbc:h2:" + folder.resolve("sessions-h2").toAbsolutePath());
    }

    @ParameterizedTest
    @MethodSource("databases")
    void aSessionResumesOnlyThroughTheHostItWasMadeThrough(String url) {
        try (Database database = AuthDatabase.open(new PluginSettings.Storage(url, "", "", 2), "sessions-test")) {
            Remember remember = new Remember(
                    new SqlRememberedSessions(database),
                    () -> new Remember.Settings(Remember.Mode.EVERYONE, Duration.ofHours(1)),
                    () -> new SharedAddresses(
                            new HostPatterns(List.of()), new AddressRanges(List.of("198.51.100.0/24"))),
                    () -> Instant.now().truncatedTo(ChronoUnit.MILLIS));
            UUID account = UUID.randomUUID();

            remember.loggedIn(account, HOME, "Play.Example.com:25565");

            assertThat(remember.resumes(account, HOME, "play.example.com.")).isTrue();
            assertThat(remember.resumes(account, HOME, "other.example.com")).isFalse();
            assertThat(remember.resumes(account, "203.0.113.8", "play.example.com"))
                    .isFalse();

            remember.loggedIn(account, FILTER, "play.example.com");
            assertThat(remember.resumes(account, FILTER, "play.example.com")).isFalse();
        }
    }
}
