package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that the three language keys reach the resolver.
 *
 * <p>The keys are the same in every plugin of ours, and an operator who writes one and gets no change has no
 * way to tell a typo from a plugin that never read the file. This is the test that says the file is read.
 */
final class PluginSettingsTest {

    @Test
    @DisplayName("the language block is read into the settings the resolver asks for")
    void readsTheLanguageBlock(@TempDir Path folder) throws IOException {
        LanguageSettings language = settingsFrom(folder, """
                language {
                  default       = "tr"
                  follow-client = false
                  force         = "de"
                }
                """);

        assertThat(language.defaultLocale()).isEqualTo(Locale.forLanguageTag("tr"));
        assertThat(language.followClient()).isFalse();
        assertThat(language.forcedLocale()).contains(Locale.forLanguageTag("de"));
    }

    @Test
    @DisplayName("an empty force means no language is forced")
    void anEmptyForceForcesNothing(@TempDir Path folder) throws IOException {
        assertThat(settingsFrom(folder, "language { force = \"\" }\n").forcedLocale())
                .isEmpty();
    }

    @Test
    @DisplayName("a file with no language block follows the client in English")
    void defaultsWithoutTheBlock(@TempDir Path folder) throws IOException {
        LanguageSettings language = settingsFrom(folder, "");

        assertThat(language.defaultLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(language.followClient()).isTrue();
        assertThat(language.forcedLocale()).isEmpty();
    }

    @Test
    @DisplayName("an empty storage URL is the SQLite file beside the configuration")
    void storageDefaultsToSqliteBesideTheFile(@TempDir Path folder) throws IOException {
        PluginSettings.Storage storage = storageFrom(folder, "");

        assertThat(storage.jdbcUrl()).isEqualTo("jdbc:sqlite:" + folder.resolve("auth.db"));
        assertThat(storage.poolSize()).isEqualTo(4);
    }

    @Test
    @DisplayName("a storage URL the operator wrote is used as written, with its account")
    void storageReadsWhatTheOperatorWrote(@TempDir Path folder) throws IOException {
        PluginSettings.Storage storage = storageFrom(folder, """
                storage {
                  jdbc-url  = "jdbc:mysql://db:3306/uxm"
                  username  = "operator"
                  password  = "secret"
                  pool-size = 8
                }
                """);

        assertThat(storage).isEqualTo(new PluginSettings.Storage("jdbc:mysql://db:3306/uxm", "operator", "secret", 8));
    }

    @Test
    @DisplayName("a pool of no connections is refused when the file is read")
    void anEmptyPoolIsRefused() {
        assertThatThrownBy(() -> new PluginSettings.Storage("jdbc:sqlite:x", "", "", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("storage.pool-size");
    }

    @Test
    @DisplayName("the storage password never reaches a log line through toString")
    void thePasswordIsNotPrinted() {
        assertThat(new PluginSettings.Storage("jdbc:mysql://db/uxm", "operator", "secret", 4).toString())
                .doesNotContain("secret");
    }

    @Test
    @DisplayName("a move target is read when it names a URL, and is absent when it names none")
    void aMoveTargetIsRead(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, """
                storage {
                  move-to { jdbc-url = " jdbc:postgresql://db:5432/uxm ", username = "mover", password = "secret" }
                }
                """, StandardCharsets.UTF_8);
        assertThat(new PluginSettings(file).current().moveTo())
                .contains(new PluginSettings.Storage("jdbc:postgresql://db:5432/uxm", "mover", "secret", 2));

        Files.writeString(file, "storage { move-to { jdbc-url = \"\" } }", StandardCharsets.UTF_8);
        assertThat(new PluginSettings(file).current().moveTo()).isEmpty();
    }

    private static PluginSettings.Storage storageFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new PluginSettings(file).current().storage();
    }

    private static LanguageSettings settingsFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new PluginSettings(file).current().language();
    }
}
