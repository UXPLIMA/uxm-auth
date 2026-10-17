package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The UUID scheme is written down on the first start, and a later start under another one is refused. */
final class RecordedSchemeTest {

    @TempDir
    Path folder;

    private Database database;

    @BeforeEach
    void open() {
        database = AuthSchema.migrate(Database.builder()
                .sqlite(folder.resolve("auth.db"))
                .poolName("uxmauth-test")
                .build());
    }

    @AfterEach
    void close() {
        database.close();
    }

    @Test
    @DisplayName("the first start writes the scheme down, and a start under the same one goes on")
    void theFirstStartRecordsTheScheme() {
        RecordedScheme recorded = new RecordedScheme(database);

        recorded.check(UuidScheme.MOJANG);
        recorded.check(UuidScheme.MOJANG);

        assertThat(recorded.recorded()).contains(UuidScheme.MOJANG);
    }

    @Test
    @DisplayName("a start under another scheme is refused, naming both and the command that moves the accounts")
    void anotherSchemeIsRefused() {
        new RecordedScheme(database).check(UuidScheme.KEEP_FIRST);

        assertThatThrownBy(() -> new RecordedScheme(database).check(UuidScheme.RANDOM))
                .isInstanceOf(SchemeChanged.class)
                .hasMessageContaining("keep-first")
                .hasMessageContaining("random")
                .hasMessageContaining("/uxmauth migrate-uuids");
        assertThat(new RecordedScheme(database).recorded()).contains(UuidScheme.KEEP_FIRST);
    }
}
