package com.uxplima.auth.adapter.outbound.premium;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.port.NameSource;
import com.uxplima.auth.application.premium.RequestCap;

/**
 * The web sources config.conf lists, in its order. Each reads its address and its timeout from the current
 * file, so a reload changes them without a restart, and Mojang alone is held to the cap.
 */
public final class PremiumSources {

    private final Supplier<AuthSettings.Premium> settings;
    private final Map<PremiumSource, NameSource> every = new EnumMap<>(PremiumSource.class);

    public PremiumSources(HttpClient client, Supplier<AuthSettings.Premium> settings, Clock clock) {
        Objects.requireNonNull(client, "client");
        this.settings = Objects.requireNonNull(settings, "settings");
        RequestCap mojangCap = new RequestCap(() -> settings.get().mojangCap(), Objects.requireNonNull(clock, "clock"));
        for (PremiumSource kind : PremiumSource.values()) {
            every.put(
                    kind,
                    new WebNameSource(
                            kind,
                            client,
                            () -> settings.get().address(kind),
                            () -> settings.get().timeout(),
                            kind == PremiumSource.MOJANG ? mojangCap::take : () -> true));
        }
    }

    /** The sources to ask, in the order config.conf lists them now. */
    public List<NameSource> ordered() {
        return settings.get().sources().stream().map(every::get).toList();
    }
}
