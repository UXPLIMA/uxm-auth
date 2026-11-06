package com.uxplima.auth.paper.adapter.inbound.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.application.message.Keys;

/**
 * Keeps every connection out while the plugin could not start.
 *
 * <p>An offline server lets anybody in as anybody while no login runs, so a start that failed leaves this in place
 * of the whole plugin. The words are the key's own English: the catalogue may be the very file that failed.
 *
 * <p>The door is shut first and again last among the listeners that may still open it, and a player some other
 * listener let through anyway is sent away as they join.
 */
public final class LockedDoor implements Listener {

    /**
     * The words when even the key cannot be read, as when its class is what failed to load. Written out here once,
     * because nothing else of the plugin may be left to read them from.
     */
    private static final String PLAIN =
            "This server's login could not start, so nobody can join until its staff fix it. Try again later.";

    private final Component reason = reason();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
    }

    /** A listener after the first one may allow the login again; this one shuts it after them. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLoginAgain(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, reason);
    }

    /** A player still let in, by a listener that runs last, is sent away before they can do anything. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().kick(reason);
    }

    /** The words a locked door sends a player away with. Never fails: a door that cannot say why still shuts. */
    public static Component reason() {
        try {
            return MiniMessage.miniMessage().deserialize(Keys.START_FAILED_KICK.defaultTemplate());
        } catch (RuntimeException | LinkageError unreadable) {
            return Component.text(PLAIN);
        }
    }
}
