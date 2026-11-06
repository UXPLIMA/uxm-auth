package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

import com.uxplima.auth.application.account.PurgeInactive;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PurgeInactiveTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final Duration WINDOW = Duration.ofDays(90);
    private static final Instant LONG_AGO = NOW.minus(Duration.ofDays(400));

    @TempDir
    Path folder;

    private static UUID idle(SqlAccountRepository accounts, String name) {
        UUID id = UUID.randomUUID();
        accounts.insert(new Account(
                id,
                new PlayerName(name),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$SHA$a$1"),
                LONG_AGO,
                "203.0.113.7",
                LONG_AGO,
                "203.0.113.7"));
        return id;
    }

    private static PurgeInactive purge(SqlAccountRepository accounts, Set<UUID> online) {
        return new PurgeInactive(
                accounts,
                accounts,
                online::contains,
                () -> new PurgeInactive.Settings(true, WINDOW, false, Duration.ofHours(24)),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void anIdleAccountGoesAndAnOnlineOneStays(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAccountRepository accounts = new SqlAccountRepository(database);
            UUID gone = idle(accounts, "Gone");
            UUID here = idle(accounts, "Here");

            assertThat(purge(accounts, Set.of(here)).tick().orElseThrow().removed())
                    .isEqualTo(1);

            assertThat(accounts.findById(gone)).isEmpty();
            assertThat(accounts.findById(here)).isPresent();
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aLoginAfterTheAccountWasChosenKeepsIt(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAccountRepository accounts = new SqlAccountRepository(database);
            UUID id = idle(accounts, "Steve");
            Instant before = NOW.minus(WINDOW);
            assertThat(accounts.inactiveSince(before, false, null, 10)).containsExactly(id);

            accounts.recordLogin(id, NOW, "203.0.113.7");

            assertThat(accounts.deleteIfInactive(id, before)).isFalse();
            assertThat(accounts.findById(id)).isPresent();
        }
    }
}
