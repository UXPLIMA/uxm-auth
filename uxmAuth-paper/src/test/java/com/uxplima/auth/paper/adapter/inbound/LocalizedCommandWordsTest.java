package com.uxplima.auth.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import com.uxplima.auth.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.annotations.Command;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves that a command can carry words for the readers of one language, and that an operator is told how.
 *
 * <p>Every plugin of ours takes {@code localized-aliases} in {@code commands.conf}. uxmLib proves what such
 * a word does: a word on a command answers anyone who types it and is shown only to its readers, and a word
 * under a command is shown to, and answered for, its readers only. This proves the two halves a plugin owns.
 * The file it ships says the field exists, and its own command takes the word from the file.
 *
 * <p>Keep it in every plugin built from the template.
 */
final class LocalizedCommandWordsTest {

    @Test
    @DisplayName("the shipped commands.conf shows how to give a command a word for one language")
    void theShippedFileDocumentsTheWords() throws IOException {
        try (InputStream in = LocalizedCommandWordsTest.class.getResourceAsStream("/commands.conf")) {
            assertThat(in).as("commands.conf on the classpath").isNotNull();
            String shipped = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);

            assertThat(shipped).contains("localized-aliases");
        }
    }

    @Test
    @DisplayName("a word for one language in commands.conf reaches the command the server registers")
    void aWordForOneLanguageReachesTheCommand(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(
                file,
                "commands { " + AdminCommand.KEY + " { localized-aliases { tr = [\"yonet\"] } } }\n",
                StandardCharsets.UTF_8);
        FromConfig declared = Objects.requireNonNull(AdminCommand.class.getAnnotation(FromConfig.class));

        Command command = (Command) ConfiguredCommands.load(file)
                .replacer()
                .replace(declared, AdminCommand.class)
                .get(0);

        assertThat(command.aliases()).contains("yonet");
        assertThat(command.localizedAliases()).containsExactly("tr=yonet");
    }
}
