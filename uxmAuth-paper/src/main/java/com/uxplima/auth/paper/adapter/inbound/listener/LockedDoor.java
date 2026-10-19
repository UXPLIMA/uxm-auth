package com.uxplima.auth.paper.adapter.inbound.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.application.message.Keys;

/**
 * Keeps every connection out while the plugin could not start.
 *
 * <p>An offline server lets anybody in as anybody while no login runs, so a start that failed leaves this in place
 * of the whole plugin. The words are the key's own English: the catalogue may be the very file that failed.
 */
public final class LockedDoor implements Listener {

    private static final Component REASON =
            MiniMessage.miniMessage().deserialize(Keys.START_FAILED_KICK.defaultTemplate());

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, REASON);
    }
}
