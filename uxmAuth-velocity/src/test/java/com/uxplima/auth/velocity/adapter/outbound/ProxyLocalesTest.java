package com.uxplima.auth.velocity.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Locale;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.velocitypowered.api.proxy.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A message on the proxy is read in the player's language, as the settings allow. */
final class ProxyLocalesTest {

    private static final Locale TURKISH = Locale.forLanguageTag("tr");

    private final Player player = mock(Player.class);

    @Test
    @DisplayName("a player is read in their client's language when the settings follow it")
    void aPlayerReadsTheirClientsLanguage() {
        when(player.getEffectiveLocale()).thenReturn(TURKISH);

        assertThat(new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)).localeOf(player))
                .isEqualTo(TURKISH);
    }

    @Test
    @DisplayName("a forced language wins over the client, and a client that sent none reads the default")
    void aForcedLanguageWins() {
        when(player.getEffectiveLocale()).thenReturn(TURKISH);
        assertThat(new ProxyLocales(() -> new LanguageSettings(Locale.ENGLISH, true, Locale.GERMAN)).localeOf(player))
                .isEqualTo(Locale.GERMAN);

        when(player.getEffectiveLocale()).thenReturn(null);
        assertThat(new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)).localeOf(player))
                .isEqualTo(Locale.ENGLISH);
    }

    @Test
    @DisplayName("the console and a player when the settings do not follow the client read the default")
    void everyoneElseReadsTheDefault() {
        when(player.getEffectiveLocale()).thenReturn(TURKISH);

        assertThat(new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)).localeOf(Audience.empty()))
                .isEqualTo(Locale.ENGLISH);
        assertThat(new ProxyLocales(() -> new LanguageSettings(Locale.ENGLISH, false, null)).localeOf(player))
                .isEqualTo(Locale.ENGLISH);
    }
}
