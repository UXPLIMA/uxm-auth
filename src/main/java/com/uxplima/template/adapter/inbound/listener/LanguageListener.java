package com.uxplima.template.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import com.uxplima.uxmlib.text.language.LanguageResolver;

/**
 * Remembers the language a player's client reports.
 *
 * <p>The client sends its language in a settings packet, which arrives a few ticks after the join event. A
 * plugin that only reads {@code Player#locale()} therefore draws the first message of a join in the wrong
 * language. Writing the reported value down means the second join is already right, and a player who never
 * chooses anything still reads the server in their own words.
 *
 * <p>The event fires again whenever a player changes the setting in their client, so the stored value stays
 * the current one.
 */
public final class LanguageListener implements Listener {

    private final LanguageResolver resolver;

    public LanguageListener(LanguageResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        resolver.rememberClient(event.getPlayer().getUniqueId(), event.locale());
    }
}
