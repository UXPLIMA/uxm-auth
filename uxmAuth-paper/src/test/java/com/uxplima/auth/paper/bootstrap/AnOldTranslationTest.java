package com.uxplima.auth.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A language file an older release wrote lacks every line a later one added. Read alone, each of those lines
 * reached a Turkish player in English, live on the lab after an update: the bare /register line did.
 */
final class AnOldTranslationTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    @Test
    @DisplayName("a line the operator's old file lacks reads the release's own translation; a line they wrote wins")
    void theShippedTranslationFillsTheGap(@TempDir Path folder) throws IOException {
        Path messages = Files.createDirectories(folder.resolve("messages"));
        Files.writeString(
                messages.resolve("messages_en.conf"), "login { already = \"Old line\" }\n", StandardCharsets.UTF_8);
        Files.writeString(
                messages.resolve("messages_tr.conf"), "login { already = \"Eski satır\" }\n", StandardCharsets.UTF_8);

        MessageCatalog catalog = PluginModule.languagesIn(messages, Locale.ENGLISH, PluginModule.class.getClassLoader())
                .catalog();

        assertThat(catalog.template(Keys.REGISTER_USAGE, TURKISH)).contains("şifre");
        assertThat(catalog.template(Keys.LOGIN_ALREADY, TURKISH)).isEqualTo("Eski satır");
    }
}
