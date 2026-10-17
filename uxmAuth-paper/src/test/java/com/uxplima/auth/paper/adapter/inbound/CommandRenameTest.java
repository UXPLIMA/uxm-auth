package com.uxplima.auth.paper.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ChangePasswordCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ClaimCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LanguageSuggestions;
import com.uxplima.auth.paper.adapter.inbound.command.LoginCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LogoutCommand;
import com.uxplima.auth.paper.adapter.inbound.command.PremiumCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RegisterCommand;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.auth.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Proves the rename all the way to the command tree.
 *
 * <p>uxmLib proves that the file is read. This proves that the value reaches the node the server would
 * register, which is the part a reader cannot check by eye. The template once shipped a
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
                  uxmauth {
                    name    = "renamed"
                    aliases = ["rn"]
                  }
                }
                """, StandardCharsets.UTF_8);

        ConfiguredCommands commands = ConfiguredCommands.load(file);
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer());

        PluginSettings settings = settingsIn(folder);
        LanguageSettings language = settings.current().language();
        LanguageChoices choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH), new LanguageResolver(language, PlayerLanguages.inMemory()), language);
        resolvers = resolvers.suggestions(LanguageSuggestions.KEY, new LanguageSuggestions(choices));
        AdminCommand handler = new AdminCommand(settings, messages(), "test", new DirectScheduler(), List::of, choices);

        assertThat(AnnotatedCommands.buildNode(handler, resolvers).getLiteral()).isEqualTo("renamed");
    }

    private static PluginSettings settingsIn(Path folder) throws IOException {
        Path config = folder.resolve("config.conf");
        Files.writeString(config, "language { default = \"en\" }\n", StandardCharsets.UTF_8);
        return new PluginSettings(config);
    }

    private static Messages messages() {
        return new Messages(
                MessageCatalogLoader.fromNodes(Map.of(), Locale.ENGLISH), LocaleSource.ofDefault(Locale.ENGLISH));
    }

    @Test
    @DisplayName("every player command takes its label from commands.conf as well")
    void everyPlayerCommandIsRenameable(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("commands.conf");
        Files.writeString(file, """
                commands {
                  login          { name = "enter" }
                  register       { name = "signup" }
                  changepassword { name = "newpass" }
                  logout         { name = "leave" }
                  claim          { name = "mine" }
                  premium        { name = "paid" }
                }
                """, StandardCharsets.UTF_8);
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, ConfiguredCommands.load(file).replacer());
        MockBukkit.mock();
        try (AuthWorld world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords())) {
            assertThat(label(
                            new LoginCommand(
                                    world.logIn,
                                    world.door,
                                    world::openingOf,
                                    world.moments,
                                    world.messages,
                                    world.replies),
                            resolvers))
                    .isEqualTo("enter");
            assertThat(label(
                            new RegisterCommand(
                                    world.register,
                                    world.door,
                                    world::openingOf,
                                    () -> world.passwords,
                                    world.messages,
                                    world.replies),
                            resolvers))
                    .isEqualTo("signup");
            assertThat(label(
                            new ChangePasswordCommand(
                                    world.change, world.moments, () -> world.passwords, world.messages, world.replies),
                            resolvers))
                    .isEqualTo("newpass");
            assertThat(label(new LogoutCommand(world.logOut, world.door, world.moments, world.messages), resolvers))
                    .isEqualTo("leave");
            ClaimName claim = new ClaimName(world.sessions, world.atTheDoor(), () -> NameRule.AUTOMATIC);
            assertThat(label(new ClaimCommand(claim, () -> true, world.messages), resolvers))
                    .isEqualTo("mine");
            assertThat(label(new PremiumCommand(claim, () -> true, world.sessions, world.messages), resolvers))
                    .isEqualTo("paid");
        } finally {
            MockBukkit.unmock();
        }
    }

    private static String label(Object handler, ParamResolvers resolvers) {
        return AnnotatedCommands.buildNode(handler, resolvers).getLiteral();
    }
}
