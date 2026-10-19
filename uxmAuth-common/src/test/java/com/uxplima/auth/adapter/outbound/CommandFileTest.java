package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.auth.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/** The words a player types for a command, read from commands.conf where the proxy has no command tree. */
final class CommandFileTest {

    @Test
    @DisplayName("a command answers to its name, its aliases and the words of every language, in lower case")
    void aCommandAnswersToEveryWord(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, """
                commands {
                  login {
                    name = "Login"
                    aliases = ["l", "log"]
                    enabled = true
                    localized-aliases { tr = ["giris"], de = ["anmelden"] }
                  }
                  register { name = "register", enabled = false }
                }
                """, StandardCharsets.UTF_8);

        assertThat(CommandFile.labels(file, "login"))
                .containsExactlyInAnyOrder("login", "l", "log", "giris", "anmelden");
        assertThat(CommandFile.labels(file, "register")).isEmpty();
        assertThat(CommandFile.labels(file, "logout")).isEmpty();
    }

    @Test
    @DisplayName("the shipped file gives /login and /register their shipped words")
    void theShippedFileGivesTheShippedWords(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        try (var shipped = CommandFileTest.class.getClassLoader().getResourceAsStream("commands.conf")) {
            Files.copy(java.util.Objects.requireNonNull(shipped), file);
        }

        assertThat(CommandFile.labels(file, "login")).contains("login", "giris");
        assertThat(CommandFile.labels(file, "register")).contains("register");
    }

    /** The file of a server installed before /unregister: one command, renamed by its operator. */
    private static final String OLD_FILE = """
            commands {
              login {
                name    = "enter"
                aliases = []
                enabled = true
              }
            }
            """;

    @Test
    @DisplayName("an old file gains the commands the release adds, and keeps the name the operator gave")
    void anOldFileGainsTheNewCommands(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, OLD_FILE, StandardCharsets.UTF_8);

        assertThat(CommandFile.bringUpToDate(file, getClass().getClassLoader())).isTrue();

        ConfigurationNode commands =
                HoconConfigurationLoader.builder().path(file).build().load().node("commands");
        assertThat(commands.node("unregister", "name").getString()).isEqualTo("unregister");
        assertThat(commands.node("cracked", "localized-aliases", "tr").getList(String.class))
                .containsExactly("crackedol");
        assertThat(commands.node("login", "name").getString()).isEqualTo("enter");
        assertThat(commands.node("login", "aliases").getList(String.class)).isEmpty();
        assertThat(CommandFile.bringUpToDate(file, getClass().getClassLoader()))
                .as("a second start adds nothing")
                .isFalse();
    }

    @Test
    @DisplayName("a server with no file yet is left to the first run")
    void anAbsentFileIsNotTouched(@TempDir Path folder) {
        Path file = folder.resolve("commands.conf");

        assertThat(CommandFile.bringUpToDate(file, getClass().getClassLoader())).isFalse();
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("every platform whose boot names commands.conf also brings it up to date")
    void everyPlatformMergesTheCommands() throws IOException {
        List<Path> writers = new ArrayList<>();
        List<Path> mergers = new ArrayList<>();
        for (Path root : Modules.mainSources()) {
            String boot = bootOf(root);
            if (boot.contains("\"commands.conf\"")) {
                writers.add(root);
            }
            if (boot.contains("CommandFile.bringUpToDate(")) {
                mergers.add(root);
            }
        }

        assertThat(writers).hasSize(2);
        assertThat(mergers).containsExactlyInAnyOrderElementsOf(writers);
    }

    /** Every bootstrap source of one module, as one text. */
    private static String bootOf(Path root) throws IOException {
        StringBuilder all = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().contains("bootstrap"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                all.append(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return all.toString();
    }
}
