package com.uxplima.auth.velocity.adapter.inbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.application.message.Keys;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;

/**
 * Keeps every connection out while the proxy's half could not start: with no login running, the network behind it
 * would take anybody under any name. The words are the key's own English, since the catalogue may be what failed.
 */
public final class LockedProxyDoor {

    private static final Component REASON =
            MiniMessage.miniMessage().deserialize(Keys.START_FAILED_KICK.defaultTemplate());

    @Subscribe(priority = Short.MAX_VALUE)
    public void onPreLogin(PreLoginEvent event) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(REASON));
    }
}
