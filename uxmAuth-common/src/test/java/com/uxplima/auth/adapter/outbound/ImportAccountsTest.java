package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.auth.application.migrate.ImportAccounts;
import com.uxplima.auth.application.migrate.ImportReport;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ImportAccountsTest {

    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String SOURCE = "authme";

    @TempDir
    Path folder;

    private static ImportAccounts importer(Database database) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new ImportAccounts(
                new SqlAccountRepository(database),
                new SqlFactors(database, SecretSeal.fromPassphrase("a passphrase only these tests use"), clock),
                new SqlImportMarks(database),
                () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                UuidScheme.OFFLINE,
                clock);
    }

    static ImportedAccount row(String name, String hash, @Nullable UUID premiumId, @Nullable Instant lastLogin) {
        return new ImportedAccount(
                name,
                null,
                premiumId,
                new LegacyHashes.Known(new PasswordHash(hash)),
                null,
                NOW.minusSeconds(3600),
                lastLogin,
                "203.0.113.7");
    }

    static ImportSource source(List<ImportedAccount> rows) {
        return new ImportSource() {
            @Override
            public String key() {
                return SOURCE;
            }

            @Override
            public Stream<ImportedAccount> read() {
                return rows.stream();
            }

            @Override
            public void close() {}
        };
    }

    @ParameterizedTest
    @EnumSource(Backend.class)
    void aSourceThatHeldNothingIsReadAgainAtTheNextStart(Backend backend) {
        try (Database database = backend.open(folder)) {
            ImportReport first = importer(database).run(source(List.of()));

            assertThat(first.empty()).isTrue();
            assertThat(new SqlImportMarks(database).completed(SOURCE)).isEmpty();

            ImportReport second = importer(database).run(source(List.of(row("Steve", "$SHA$a$1", null, null))));

            assertThat(second.imported()).isEqualTo(1);
            assertThat(new SqlImportMarks(database).completed(SOURCE)).isPresent();
        }
    }
}
