package com.uxplima.template.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.template.adapter.inbound.command.ExampleCommand;
import com.uxplima.template.adapter.outbound.InMemoryExampleRepository;
import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.CreateExample;
import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.language.HeldChoice;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageNames;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * An operator sets the language another player reads, from the console as well as in game.
 *
 * <p>A server with one audience asked for this on uxmEssentials issue 2: its players' clients read English and
 * the server is German. The choice lands on the player's own data, so every plugin of ours reads it, and a
 * player who is away gets it when they join.
 */
final class LanguageOfAnotherPlayerTest {

    private static final Locale TR = Locale.forLanguageTag("tr");

    private ServerMock server;

    private PlayerLanguages store;

    private Messages messages;

    private ExampleCommand command;

    @BeforeEach
    void setUp(@TempDir Path folder) throws IOException {
        server = MockBukkit.mock();
        command = commandWith(folder, "language { default = \"en\" }\n");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player online reads the language at once, in this plugin and in every other")
    void online() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.setLocale(Locale.ENGLISH);

        command.setLanguageOf(console(), "steve", "tr");

        assertThat(messages.localeOf(steve)).isEqualTo(TR);
        assertThat(anotherPlugin().localeOf(steve)).isEqualTo(TR);
        assertThat(steve.nextComponentMessage()).isEqualTo(messages.render(steve, Keys.LANGUAGE_CHANGED, language()));
    }

    @Test
    @DisplayName("a player who is away has the language held until they join")
    void away() {
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();

        command.setLanguageOf(console(), "Alex", "tr");

        assertThat(store.held(alex.getUniqueId())).contains(new HeldChoice(TR));
    }

    @Test
    @DisplayName("a name nobody played under changes nothing")
    void nobody() {
        command.setLanguageOf(console(), "Nobody", "tr");

        ConsoleCommandSenderMock said = server.getConsoleSender();
        assertThat(said.nextComponentMessage())
                .isEqualTo(messages.render(said, Keys.LANGUAGE_UNKNOWN_PLAYER, Text.placeholder("input", "Nobody")));
    }

    @Test
    @DisplayName("a reset clears the language in every plugin, and the player reads the default again")
    void reset() {
        PlayerMock steve = server.addPlayer("Steve");
        steve.setLocale(Locale.ENGLISH);
        command.setLanguageOf(console(), "Steve", "tr");

        command.resetLanguageOf(console(), "Steve");

        assertThat(anotherPlugin().localeOf(steve)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("a server that does not follow the client starts every player in its default")
    void oneAudience(@TempDir Path folder) throws IOException {
        command = commandWith(folder, "language { default = \"tr\", follow-client = false }\n");
        PlayerMock steve = server.addPlayer("Steve");
        steve.setLocale(Locale.ENGLISH);

        assertThat(messages.localeOf(steve)).isEqualTo(TR);
    }

    private ExampleCommand commandWith(Path folder, String config) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, config, StandardCharsets.UTF_8);
        PluginSettings settings = new PluginSettings(file);
        store = PlayerLanguages.inMemory();
        LanguageResolver resolver = new LanguageResolver(settings.current().language(), store);
        messages = new Messages(
                new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
                resolver);
        LanguageChoices choices = new LanguageChoices(
                () -> Set.of(Locale.ENGLISH, TR), resolver, settings.current().language());
        return new ExampleCommand(
                new CreateExample(new InMemoryExampleRepository(), 3),
                new InMemoryExampleRepository(),
                messages,
                choices,
                settings);
    }

    private static LanguageResolver anotherPlugin() {
        return new LanguageResolver(LanguageSettings.following(Locale.ENGLISH), PlayerLanguages.inMemory());
    }

    private static TagResolver language() {
        return Text.placeholder("language", LanguageNames.of(TR));
    }

    private Sender console() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandSender sender = server.getConsoleSender();
        when(source.getSender()).thenReturn(sender);
        return Sender.of(source);
    }
}
