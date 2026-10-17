package com.uxplima.auth.velocity.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

import net.kyori.adventure.audience.Audience;

import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.velocitypowered.api.proxy.Player;

/**
 * The language a message on the proxy is read in. A player on the proxy has no language chosen on a server, so
 * the client's own is the answer, unless the operator forces one or follows no client.
 */
public final class ProxyLocales implements LocaleSource {

    private final Supplier<LanguageSettings> settings;

    public ProxyLocales(Supplier<LanguageSettings> settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public Locale defaultLocale() {
        return settings.get().defaultLocale();
    }

    @Override
    public Locale localeOf(Audience viewer) {
        Objects.requireNonNull(viewer, "viewer");
        LanguageSettings now = settings.get();
        Locale forced = now.forced();
        if (forced != null) {
            return forced;
        }
        if (now.followClient() && viewer instanceof Player player) {
            Locale client = player.getEffectiveLocale();
            if (client != null) {
                return client;
            }
        }
        return now.defaultLocale();
    }
}
