package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;

import com.uxplima.uxmlib.storage.sql.Database;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** An import is marked complete where every server of a network reads it, once, and a second mark is harmless. */
final class SqlImportMarksTest {

    @TempDir
    Path folder;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("an import not run is unmarked; once marked it reads back its moment, and marking again moves it")
    void marksAreKept(String engine) {
        String url = engine.equals("h2")
                ? "jdbc:h2:" + folder.resolve("h2").toAbsolutePath()
                : "jdbc:sqlite:" + folder.resolve("accounts.db");
        try (Database database =
                AuthDatabase.open(new PluginSettings.Storage(url, engine.equals("h2") ? "sa" : "", "", 2), "marks")) {
            SqlImportMarks marks = new SqlImportMarks(database);
            Instant first = Instant.parse("2026-10-18T12:00:00Z");

            assertThat(marks.completed("authme")).isEmpty();
            marks.complete("authme", first);
            assertThat(marks.completed("authme")).contains(first);
            assertThat(marks.completed("nlogin")).isEmpty();
            marks.complete("authme", first.plusSeconds(60));
            assertThat(marks.completed("authme")).contains(first.plusSeconds(60));
            assertThat(new RecordedScheme(database).recorded())
                    .as("the scheme's own row in the same table is untouched")
                    .isEmpty();
        }
    }
}
