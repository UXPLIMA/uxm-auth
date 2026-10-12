package com.uxplima.template.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.template.adapter.inbound.command.LanguageOfAnotherPlayer;
import com.uxplima.template.application.message.Keys;
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

    private LanguageOfAnotherPlayer another;

    private ConsoleCommandSenderMock console;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        console = server.getConsoleSender();
        another = with(LanguageSettings.following(Locale.ENGLISH));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player online reads the language at once, in this plugin and in every other")
    void online() {
        PlayerMock steve = english("Steve");

        another.set(console, "steve", "tr");

        assertThat(messages.localeOf(steve)).isEqualTo(TR);
        assertThat(anotherPlugin().localeOf(steve)).isEqualTo(TR);
        assertThat(steve.nextComponentMessage()).isEqualTo(messages.render(steve, Keys.LANGUAGE_CHANGED, turkish()));
        assertThat(console.nextComponentMessage())
                .isEqualTo(messages.render(
                        console, Keys.LANGUAGE_SET_PLAYER, Text.placeholder("player", "Steve"), turkish()));
    }

    @Test
    @DisplayName("a player who is away has the language held until they join")
    void away() {
        PlayerMock alex = server.addPlayer("Alex");
        alex.disconnect();

        another.set(console, "Alex", "tr");

        assertThat(store.held(alex.getUniqueId())).contains(new HeldChoice(TR));
    }

    @Test
    @DisplayName("a name nobody played under changes nothing and says so")
    void nobody() {
        another.set(console, "Nobody", "tr");

        assertThat(console.nextComponentMessage())
                .isEqualTo(messages.render(console, Keys.LANGUAGE_UNKNOWN_PLAYER, Text.placeholder("input", "Nobody")));
    }

    @Test
    @DisplayName("a language this plugin does not have changes nothing")
    void noSuchLanguage() {
        PlayerMock steve = english("Steve");

        another.set(console, "Steve", "xx");

        assertThat(messages.localeOf(steve)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("a reset clears the language in every plugin")
    void reset() {
        PlayerMock steve = english("Steve");
        another.set(console, "Steve", "tr");

        another.reset(console, "Steve");

        assertThat(anotherPlugin().localeOf(steve)).isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("while a language is forced the choice is kept and the operator is told it waits")
    void forced() {
        another = with(new LanguageSettings(Locale.ENGLISH, true, Locale.GERMAN));
        PlayerMock steve = english("Steve");

        another.set(console, "Steve", "tr");

        assertThat(anotherPlugin().localeOf(steve)).isEqualTo(TR);
        console.nextComponentMessage();
        assertThat(console.nextComponentMessage())
                .isEqualTo(messages.render(
                        console,
                        Keys.LANGUAGE_FORCED_ANYWAY,
                        Text.placeholder("language", LanguageNames.of(Locale.GERMAN))));
    }

    @Test
    @DisplayName("a server that does not follow the client starts every player in its default")
    void oneAudience() {
        another = with(new LanguageSettings(TR, false, null));
        PlayerMock steve = english("Steve");

        assertThat(messages.localeOf(steve)).isEqualTo(TR);
    }

    private LanguageOfAnotherPlayer with(LanguageSettings settings) {
        store = PlayerLanguages.inMemory();
        LanguageResolver resolver = new LanguageResolver(settings, store);
        messages = new Messages(
                new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
                resolver);
        LanguageChoices choices = new LanguageChoices(() -> Set.of(Locale.ENGLISH, TR), resolver, settings);
        return new LanguageOfAnotherPlayer(messages, choices, () -> Optional.ofNullable(settings.forced()));
    }

    private PlayerMock english(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setLocale(Locale.ENGLISH);
        return player;
    }

    private static LanguageResolver anotherPlugin() {
        return new LanguageResolver(LanguageSettings.following(Locale.ENGLISH), PlayerLanguages.inMemory());
    }

    private static TagResolver turkish() {
        return Text.placeholder("language", LanguageNames.of(TR));
    }
}
