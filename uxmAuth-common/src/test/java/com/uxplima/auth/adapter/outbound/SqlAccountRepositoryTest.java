package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SqlAccountRepositoryTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final PlayerName STEVE = new PlayerName("Steve");

    @TempDir
    Path folder;

    private static Account cracked(UUID id, PlayerName name) {
        return new Account(
                id,
                name,
                AccountKind.CRACKED,
                null,
                new PasswordHash("$SHA$salt$hash"),
                NOW,
                "203.0.113.7",
                null,
                null);
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aNameKeptForAnAccountIsNotKeptForTheNextOneUnderItsId(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAccountRepository accounts = new SqlAccountRepository(database);
            SqlDisputes disputes = new SqlDisputes(database);
            UUID id = UUID.randomUUID();
            accounts.insert(cracked(id, STEVE));
            disputes.raise(STEVE, UUID.randomUUID(), NOW);
            disputes.keep(STEVE, id);

            accounts.delete(id);

            assertThat(disputes.of(STEVE)).isEmpty();
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aMovedIdKeepsTheNameStaffKeptForIt(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAccountRepository accounts = new SqlAccountRepository(database);
            SqlDisputes disputes = new SqlDisputes(database);
            UUID from = UUID.randomUUID();
            UUID to = UUID.randomUUID();
            accounts.insert(cracked(from, STEVE));
            disputes.raise(STEVE, UUID.randomUUID(), NOW);
            disputes.keep(STEVE, from);

            accounts.moveId(from, to);

            assertThat(disputes.of(STEVE).orElseThrow().keptFor()).isEqualTo(to);
        }
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void anUnsettledDisputeOutlivesTheHolder(Backend backend) {
        try (Database database = backend.open(folder)) {
            SqlAccountRepository accounts = new SqlAccountRepository(database);
            SqlDisputes disputes = new SqlDisputes(database);
            UUID id = UUID.randomUUID();
            accounts.insert(cracked(id, STEVE));
            disputes.raise(STEVE, UUID.randomUUID(), NOW);

            accounts.delete(id);

            assertThat(disputes.of(STEVE).orElseThrow().kept()).isFalse();
        }
    }
}
