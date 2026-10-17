package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.language.LanguageService;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.language.PlayerLanguages;
import com.uxplima.uxmlib.text.message.LocaleSource;

/**
 * The language of a reader who may not be a player yet.
 *
 * <p>uxmLib's resolver answers a player. A client in the configuration phase has none, so this answers it in
 * the resolver's own order from the same store and the same provider: the language the server forces, the one
 * the player chose on an earlier visit, then the client's own while the server follows it, then the default.
 * Every other reader goes to the resolver unchanged.
 */
public final class ConnectingLocales implements LocaleSource {

    private final LocaleSource players;
    private final Supplier<LanguageSettings> settings;
    private final PlayerLanguages store;
    private final Supplier<Optional<LanguageService>> service;

    public ConnectingLocales(
            LocaleSource players,
            Supplier<LanguageSettings> settings,
            PlayerLanguages store,
            Supplier<Optional<LanguageService>> service) {
        this.players = Objects.requireNonNull(players, "players");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.store = Objects.requireNonNull(store, "store");
        this.service = Objects.requireNonNull(service, "service");
    }

    @Override
    public Locale defaultLocale() {
        return players.defaultLocale();
    }

    @Override
    public Locale localeOf(Audience viewer) {
        Objects.requireNonNull(viewer, "viewer");
        if (!(viewer instanceof ConnectingAudience connecting)) {
            return players.localeOf(viewer);
        }
        LanguageSettings now = settings.get();
        UUID id = connecting.id();
        return now.forcedLocale()
                .or(() -> service.get().flatMap(provider -> provider.languageOf(id)))
                .or(() -> store.chosen(id))
                .or(() -> now.followClient() ? fromClient(connecting) : Optional.empty())
                .orElseGet(now::defaultLocale);
    }

    // The client speaks for itself in this phase; what was remembered from an earlier visit only fills in
    // for a client that has not said it yet.
    private Optional<Locale> fromClient(ConnectingAudience connecting) {
        UUID id = connecting.id();
        return connecting
                .client()
                .or(() -> service.get().flatMap(provider -> provider.lastClientLanguageOf(id)))
                .or(() -> store.lastClient(id));
    }
}
