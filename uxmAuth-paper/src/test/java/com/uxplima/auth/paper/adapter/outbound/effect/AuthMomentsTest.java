package com.uxplima.auth.paper.adapter.outbound.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.testing.DirectScheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The effect list an operator wrote for a moment reaches the player it happened to. */
final class AuthMomentsTest {

    private ServerMock server;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        // Plain words, so the test reads what reached the player and not how the theme painted it.
        messages = new Messages(
                new MessageCatalog(
                        Map.of(
                                Locale.ENGLISH,
                                Map.of(
                                        "welcome.registered",
                                        "Your account is ready",
                                        "welcome.logged-in",
                                        "Logged in")),
                        Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a line that names a key sends that key's words in the player's language")
    void aKeyLineSendsItsWords() {
        PlayerMock steve = server.addPlayer("Steve");

        moments(Map.of(AuthSettings.Moment.REGISTERED, List.of("[message] @welcome.registered")))
                .fire(steve, AuthSettings.Moment.REGISTERED);

        assertThat(plain(steve.nextComponentMessage())).isEqualTo("Your account is ready");
    }

    @Test
    @DisplayName("an empty list is silence, and a line the engine cannot read costs only that line")
    void emptyIsSilenceAndABadLineCostsOnlyItself() {
        PlayerMock steve = server.addPlayer("Steve");

        moments(Map.of(
                        AuthSettings.Moment.LOGGED_IN,
                        List.of("[shout] nothing", "[message] @welcome.logged-in"),
                        AuthSettings.Moment.LOGGED_OUT,
                        List.of()))
                .fire(steve, AuthSettings.Moment.LOGGED_OUT);
        assertThat(steve.nextComponentMessage()).isNull();

        moments(Map.of(AuthSettings.Moment.LOGGED_IN, List.of("[shout] nothing", "[message] @welcome.logged-in")))
                .fire(steve, AuthSettings.Moment.LOGGED_IN);
        assertThat(plain(steve.nextComponentMessage())).isEqualTo("Logged in");
    }

    @Test
    @DisplayName("the player's name and id are filled in a line without PlaceholderAPI")
    void theNameAndIdNeedNoPlaceholderPlugin() {
        PlayerMock steve = server.addPlayer("Steve");

        moments(Map.of(AuthSettings.Moment.LOGGED_IN, List.of("[message] Hello %player_name% (%player_uuid%)")))
                .fire(steve, AuthSettings.Moment.LOGGED_IN);

        assertThat(plain(steve.nextComponentMessage())).isEqualTo("Hello Steve (" + steve.getUniqueId() + ")");
    }

    private static @Nullable String plain(@Nullable Component line) {
        return line == null ? null : PlainTextComponentSerializer.plainText().serialize(line);
    }

    private AuthMoments moments(Map<AuthSettings.Moment, List<String>> lists) {
        AuthSettings.Effects effects = new AuthSettings.Effects(new EnumMap<>(lists));
        return new AuthMoments(server, new DirectScheduler(), messages, new Styler(Theme.defaults()), () -> effects);
    }
}
