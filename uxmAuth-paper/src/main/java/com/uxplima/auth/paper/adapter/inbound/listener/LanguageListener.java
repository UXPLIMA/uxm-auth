package com.uxplima.auth.paper.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;

import com.uxplima.uxmlib.text.language.LanguageResolver;

/**
 * Remembers the language a player's client reports, and hands a joining player the language that waited for
 * them.
 *
 * <p>The client sends its language in a settings packet, which arrives a few ticks after the join event. A
 * plugin that only reads {@code Player#locale()} therefore draws the first message of a join in the wrong
 * language. Writing the reported value down means the second join is already right, and a player who never
 * chooses anything still reads the server in their own words.
 *
 * <p>The event fires again whenever a player changes the setting in their client, so the stored value stays
 * the current one.
 *
 * <p>An operator may set the language of a player who is away. The choice is kept on the player's own data,
 * which the server can only write while they are on it, so it waits in this plugin until they join and is
 * applied here. Reading the shared choice on the player's own thread here also means a message drawn
 * elsewhere for them already knows it.
 */
public final class LanguageListener implements Listener {

    private final LanguageResolver resolver;

    public LanguageListener(LanguageResolver resolver) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        resolver.settle(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        resolver.rememberClient(event.getPlayer().getUniqueId(), event.locale());
    }
}
