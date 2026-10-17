package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.language.LanguageService;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.LocaleSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A client still connecting has no player yet, and still reads its screens in the order a player would. */
final class ConnectingLocalesTest {

    private static final Locale EN = Locale.ENGLISH;
    private static final Locale TR = Locale.forLanguageTag("tr");
    private static final Locale DE = Locale.GERMAN;
    private static final Locale PLAYERS = Locale.JAPANESE;
    private static final UUID WHO = UUID.randomUUID();

    private final PlayerLanguages store = PlayerLanguages.inMemory();
    private @Nullable LanguageService service;
    private LanguageSettings settings = new LanguageSettings(EN, true, null);

    @Test
    @DisplayName("the language the server forces wins over everything")
    void theForcedLanguageWins() {
        settings = new LanguageSettings(EN, true, DE);
        store.choose(WHO, TR);

        assertThat(locales().localeOf(connecting(TR))).isEqualTo(DE);
    }

    @Test
    @DisplayName("a language the player chose on an earlier visit wins over the client")
    void theOwnChoiceWinsOverTheClient() {
        store.choose(WHO, TR);

        assertThat(locales().localeOf(connecting(DE))).isEqualTo(TR);
    }

    @Test
    @DisplayName("a language provider's choice wins over this plugin's own store")
    void theProviderWinsOverTheStore() {
        store.choose(WHO, TR);
        service = new Fixed(DE);

        assertThat(locales().localeOf(connecting(EN))).isEqualTo(DE);
    }

    @Test
    @DisplayName("with nothing chosen, the client's own language is read")
    void theClientIsRead() {
        assertThat(locales().localeOf(connecting(TR))).isEqualTo(TR);
    }

    @Test
    @DisplayName("a server that does not follow the client gives the default")
    void notFollowingGivesTheDefault() {
        settings = new LanguageSettings(EN, false, null);

        assertThat(locales().localeOf(connecting(TR))).isEqualTo(EN);
    }

    @Test
    @DisplayName("a client that has not said its language yet gives the default")
    void aSilentClientGivesTheDefault() {
        assertThat(locales().localeOf(connecting(null))).isEqualTo(EN);
    }

    @Test
    @DisplayName("anyone who is not connecting is asked of the players' resolver")
    void everyoneElseIsAskedOfTheResolver() {
        assertThat(locales().localeOf(Audience.empty())).isEqualTo(PLAYERS);
    }

    private ConnectingLocales locales() {
        LocaleSource players = new LocaleSource() {
            @Override
            public Locale defaultLocale() {
                return EN;
            }

            @Override
            public Locale localeOf(Audience viewer) {
                return PLAYERS;
            }
        };
        return new ConnectingLocales(players, () -> settings, store, () -> Optional.ofNullable(service));
    }

    private static ConnectingAudience connecting(@Nullable Locale client) {
        return new ConnectingAudience(WHO, Audience.empty(), client);
    }

    private record Fixed(Locale locale) implements LanguageService {

        @Override
        public Optional<Locale> languageOf(UUID player) {
            return Optional.of(locale);
        }

        @Override
        public void choose(UUID player, Locale chosen) {}

        @Override
        public void forget(UUID player) {}
    }
}
