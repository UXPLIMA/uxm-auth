package com.uxplima.template.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

import com.uxplima.template.adapter.inbound.command.AdminCommand;
import com.uxplima.template.adapter.inbound.command.ConfiguredCommands;
import com.uxplima.template.adapter.inbound.command.FromConfig;
import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the rename all the way to the command tree.
 *
 * <p>{@code ConfiguredCommandsTest} proves the file is read. This proves the value reaches the node the
 * server would register, which is the part a reader cannot check by eye. The template once shipped a
 * {@code commands.conf} that nothing read: it was documented, it looked correct in review, and every
 * command kept its compiled-in label. This test is what makes that failure impossible to repeat.
 *
 * <p>Keep it in every plugin built from this template.
 */
final class CommandRenameTest {

    @Test
    @DisplayName("the built command node takes its label from commands.conf")
    void nodeTakesTheConfiguredLabel(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, """
                commands {
                  uxmtemplate {
                    name    = "renamed"
                    aliases = ["rn"]
                  }
                }
                """, StandardCharsets.UTF_8);

        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer());

        AdminCommand handler = new AdminCommand(settingsIn(folder), messages(), "test");

        assertThat(AnnotatedCommands.buildNode(handler, resolvers).getLiteral()).isEqualTo("renamed");
    }

    private static PluginSettings settingsIn(Path folder) throws IOException {
        Path config = folder.resolve("config.conf");
        Files.writeString(config, "locale = \"en\"\n", StandardCharsets.UTF_8);
        return new PluginSettings(config);
    }

    private static Messages messages() {
        return new Messages(
                MessageCatalogLoader.fromNodes(Map.of(), Locale.ENGLISH), LocaleSource.ofDefault(Locale.ENGLISH));
    }
}
