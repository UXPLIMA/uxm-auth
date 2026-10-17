package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordVerdict;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The blocks of config.conf that decide how a player gets in. */
final class AuthSettingsTest {

    @Test
    @DisplayName("a file that says nothing gets the defaults the plan and the brief chose")
    void defaultsMatchThisPlan(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "");

        assertThat(settings.names()).isEqualTo(new AuthSettings.Names("[A-Za-z0-9_]+", 3, 16, List.of()));
        assertThat(settings.passwords()).isEqualTo(new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), true, true, 3));
        assertThat(settings.hashing())
                .isEqualTo(new AuthSettings.HashSettings(HashAlgorithm.ARGON2ID, 65536, 3, 1, 12, 600_000));
        assertThat(settings.lane()).isEqualTo(new AuthSettings.Lane(2, 4, 256));
        assertThat(settings.timeouts())
                .isEqualTo(new AuthSettings.Timeouts(
                        Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5)));
        assertThat(settings.hold().movement()).isTrue();
        assertThat(settings.hold().allowedCommands()).isEmpty();
        assertThat(settings.hold().hideStats()).isTrue();
    }

    @Test
    @DisplayName("the shipped file says exactly what the code defaults to")
    void theShippedFileSaysTheDefaults(@TempDir Path folder) throws IOException {
        Path shipped = folder.resolve("shipped");
        Files.createDirectories(shipped);
        try (InputStream file = Objects.requireNonNull(getClass().getResourceAsStream("/config.conf"))) {
            Files.copy(file, shipped.resolve("config.conf"));
        }
        PluginSettings.Snapshot fromShipped = new PluginSettings(shipped.resolve("config.conf")).current();
        PluginSettings.Snapshot fromNothing = settingsFrom(shipped, "");

        assertThat(fromShipped).isEqualTo(fromNothing);
    }

    @Test
    @DisplayName("an algorithm this plugin does not have is refused, naming the line")
    void anUnknownHashAlgorithmIsRefusedWithItsPath(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "hashing { algorithm = \"md5\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hashing.algorithm")
                .hasMessageContaining("md5");
    }

    @Test
    @DisplayName("bcrypt with a maximum past its 72 bytes is refused, because the rest would be ignored")
    void bcryptWithAMaximumOver72BytesIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, """
                        hashing { algorithm = "bcrypt" }
                        passwords { max-length = 73 }
                        """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("passwords.max-length")
                .hasMessageContaining("72");
    }

    @Test
    @DisplayName("bcrypt within its limit also caps the bytes, so a long password in a wide script is told why")
    void bcryptCapsTheBytes(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, """
                hashing { algorithm = "bcrypt" }
                passwords { max-length = 64 }
                """);

        assertThat(settings.passwordPolicy().check(Password.of("ş".repeat(40))))
                .isEqualTo(new PasswordVerdict.TooLongForTheHash(72));
    }

    @Test
    @DisplayName("a pattern that does not compile is refused, naming the line")
    void aBrokenPatternIsRefused(@TempDir Path folder) {
        assertThatThrownBy(() -> settingsFrom(folder, "names { pattern = \"[a-z\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("names.pattern");
    }

    @Test
    @DisplayName("the required classes are read by their names")
    void readsTheRequiredClasses(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "passwords { required = [\"digit\", \"upper\"] }\n");

        assertThat(settings.passwords().required()).containsExactlyInAnyOrder(CharClass.DIGIT, CharClass.UPPER);
    }

    @Test
    @DisplayName("an effect list the operator emptied stays empty, and one never written is the shipped one")
    void effectsKeepAnEmptyList(@TempDir Path folder) throws IOException {
        PluginSettings.Snapshot settings = settingsFrom(folder, "effects { logged-in = [] }\n");

        assertThat(settings.effects().of(AuthSettings.Moment.LOGGED_IN)).isEmpty();
        assertThat(settings.effects().of(AuthSettings.Moment.REGISTERED)).isNotEmpty();
    }

    @Test
    @DisplayName("a reload swaps in the whole new file at once")
    void reloadSwapsTheWholeSnapshot(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "passwords { min-length = 6 }\nlane { threads = 2 }\n", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        PluginSettings.Snapshot before = settings.current();

        Files.writeString(file, "passwords { min-length = 8 }\nlane { threads = 3 }\n", StandardCharsets.UTF_8);
        settings.reload();

        assertThat(before.passwords().minLength()).isEqualTo(6);
        assertThat(settings.current().passwords().minLength()).isEqualTo(8);
        assertThat(settings.current().lane().threads()).isEqualTo(3);
    }

    @Test
    @DisplayName("a reload that fails keeps the snapshot that worked")
    void aBrokenReloadKeepsTheOldSnapshot(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "", StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        PluginSettings.Snapshot before = settings.current();

        Files.writeString(file, "hashing { algorithm = \"md5\" }\n", StandardCharsets.UTF_8);

        assertThatThrownBy(settings::reload).isInstanceOf(IllegalArgumentException.class);
        assertThat(settings.current()).isSameAs(before);
    }

    private static PluginSettings.Snapshot settingsFrom(Path folder, String body) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        return new PluginSettings(file).current();
    }
}
