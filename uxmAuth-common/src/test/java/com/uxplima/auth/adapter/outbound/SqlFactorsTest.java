package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** An account's app secret, sealed, with the last step it was used for, and its backup codes, hashed. */
final class SqlFactorsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
    private static final TotpSecret SECRET = new TotpSecret("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @TempDir
    Path folder;

    private Database database;
    private SqlFactors factors;

    @BeforeEach
    void open() {
        database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "factor-test");
        factors = new SqlFactors(database, SecretSeal.fromPassphrase("lab"), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("an app secret is kept sealed and read back whole, with no step used yet")
    void anAppSecretIsKeptSealed() {
        UUID steve = UUID.randomUUID();

        factors.setApp(steve, SECRET);

        assertThat(factors.app(steve)).hasValueSatisfying(app -> {
            assertThat(app.secret()).isEqualTo(SECRET);
            assertThat(app.lastStep()).isEqualTo(-1L);
        });
        assertThat(factors.app(UUID.randomUUID())).isEmpty();
        String stored = database.transaction(sql -> sql.queryFirst(
                        "SELECT secret FROM uxmauth_factor WHERE account_id = ?",
                        statement -> statement.setString(1, steve.toString()),
                        row -> row.getString("secret")))
                .orElseThrow();
        assertThat(stored).doesNotContain(SECRET.base32()).doesNotContain("1234567890");
    }

    @Test
    @DisplayName("a step is taken once, only past the last one, across every server that shares the table")
    void aStepIsTakenOnce() {
        UUID steve = UUID.randomUUID();
        factors.setApp(steve, SECRET);
        SqlFactors otherServer =
                new SqlFactors(database, SecretSeal.fromPassphrase("lab"), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(factors.useStep(steve, 100)).isTrue();
        assertThat(otherServer.useStep(steve, 100)).isFalse();
        assertThat(factors.useStep(steve, 99)).isFalse();
        assertThat(otherServer.useStep(steve, 101)).isTrue();
        assertThat(factors.app(steve))
                .hasValueSatisfying(app -> assertThat(app.lastStep()).isEqualTo(101L));
        assertThat(factors.useStep(UUID.randomUUID(), 5)).isFalse();
    }

    @Test
    @DisplayName("a new secret starts over, turning the app off removes it, and an empty set clears the backup codes")
    void anAppIsReplacedAndRemoved() {
        UUID steve = UUID.randomUUID();
        factors.setApp(steve, SECRET);
        factors.useStep(steve, 100);
        factors.setBackupCodes(steve, List.of(new PasswordHash("$hash$one")));

        factors.setApp(steve, new TotpSecret(new byte[20]));
        assertThat(factors.app(steve))
                .hasValueSatisfying(app -> assertThat(app.lastStep()).isEqualTo(-1L));

        factors.removeApp(steve);
        assertThat(factors.app(steve)).isEmpty();
        assertThat(factors.backupCodes(steve)).containsExactly(new PasswordHash("$hash$one"));
        factors.setBackupCodes(steve, List.of());
        assertThat(factors.backupCodes(steve)).isEmpty();
    }

    @Test
    @DisplayName("a new set of backup codes voids the old one, and a code is used once")
    void backupCodes() {
        UUID steve = UUID.randomUUID();
        UUID alex = UUID.randomUUID();
        factors.setBackupCodes(steve, List.of(new PasswordHash("$hash$one"), new PasswordHash("$hash$two")));
        factors.setBackupCodes(alex, List.of(new PasswordHash("$hash$one")));

        factors.setBackupCodes(steve, List.of(new PasswordHash("$hash$three"), new PasswordHash("$hash$four")));
        assertThat(factors.backupCodes(steve))
                .containsExactlyInAnyOrder(new PasswordHash("$hash$three"), new PasswordHash("$hash$four"));

        assertThat(factors.useBackupCode(steve, new PasswordHash("$hash$three")))
                .isTrue();
        assertThat(factors.useBackupCode(steve, new PasswordHash("$hash$three")))
                .isFalse();
        assertThat(factors.useBackupCode(steve, new PasswordHash("$hash$one"))).isFalse();
        assertThat(factors.backupCodes(steve)).containsExactly(new PasswordHash("$hash$four"));
        assertThat(factors.backupCodes(alex)).containsExactly(new PasswordHash("$hash$one"));
    }

    @Test
    @DisplayName("on a database that reads committed rows, of many servers taking one step together only one wins")
    void oneStepIsTakenOnceOnAServerDatabase() throws Exception {
        Database h2 = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:h2:" + folder.resolve("server").toAbsolutePath(), "sa", "", 8),
                "factor-test-h2");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            SqlFactors shared = new SqlFactors(h2, SecretSeal.fromPassphrase("lab"), Clock.fixed(NOW, ZoneOffset.UTC));
            UUID steve = UUID.randomUUID();
            shared.setApp(steve, SECRET);
            List<CompletableFuture<Boolean>> sent = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                sent.add(CompletableFuture.supplyAsync(() -> shared.useStep(steve, 100), pool));
            }

            assertThat(sent.stream().filter(CompletableFuture::join).count()).isEqualTo(1);
            assertThat(shared.app(steve))
                    .hasValueSatisfying(app -> assertThat(app.secret()).isEqualTo(SECRET));
        } finally {
            pool.shutdownNow();
            h2.close();
        }
    }
}
