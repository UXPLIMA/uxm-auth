package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The labels the hold lets through and the log filter masks come from commands.conf, not from the code. */
final class PlayerCommandLabelsTest {

    @Test
    @DisplayName("every name, alias and word for one language the shipped file gives the password commands")
    void theShippedLabels(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        try (InputStream shipped = Objects.requireNonNull(getClass().getResourceAsStream("/commands.conf"))) {
            Files.copy(shipped, file);
        }

        assertThat(PlayerCommandLabels.of(ConfiguredCommands.load(file)))
                .contains("login", "l", "register", "reg", "changepassword", "logout", "giris", "kayit")
                .doesNotContain("uxmauth", "auth");
    }

    @Test
    @DisplayName("a label an operator renames is the label that counts")
    void aRenameReachesTheLabels(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, "commands { login { name = \"Enter\", aliases = [\"IN\"] } }\n");

        assertThat(PlayerCommandLabels.of(ConfiguredCommands.load(file)))
                .contains("enter", "in")
                .doesNotContain("login");
    }
}
