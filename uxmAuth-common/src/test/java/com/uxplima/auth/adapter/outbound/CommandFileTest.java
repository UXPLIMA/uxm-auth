package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
