package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A file an earlier release wrote keeps the name its Discord lines give this server, under the shared name. */
class ServerNameTest {

    @TempDir
    Path folder;

    @Test
    void theOldDiscordServerBecomesTheServerName() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, "discord {\n  server = \"survival\"\n}\n");

        assertThat(PluginSettings.bringUpToDate(file, getClass().getClassLoader()))
                .isTrue();

        HoconConfig config = HoconConfig.load(file);
        assertThat(config.getString("discord.server-name", "")).isEqualTo("survival");
        assertThat(config.nodeAt("discord", "server").virtual()).isTrue();
    }
}
