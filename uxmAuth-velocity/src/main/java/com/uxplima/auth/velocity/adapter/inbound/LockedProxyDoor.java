package com.uxplima.auth.velocity.adapter.inbound;

import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.auth.application.message.Keys;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;

/**
 * Keeps every connection out while the proxy's half could not start: with no login running, the network behind it
 * would take anybody under any name.
 *
 * <p>The door is shut first and again last at the pre-login, because a plugin between may open it again: Floodgate
 * puts a Bedrock connection in offline mode whatever the result before it was. A connection some plugin still lets
 * through is turned away at its login.
 */
public final class LockedProxyDoor {

    private final Component reason;

    /** The words are the key's own English, for a start that failed before it could read the operator's. */
    public LockedProxyDoor() {
        this(defaultReason());
    }

    /** @param reason the words a connection is turned away with, in the operator's language when the start read it */
    public LockedProxyDoor(Component reason) {
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    /** The key's own English. Never fails: a door that cannot say why still shuts. */
    public static Component defaultReason() {
        return MiniMessage.miniMessage().deserialize(Keys.START_FAILED_KICK.defaultTemplate());
    }

    @Subscribe(priority = Short.MAX_VALUE)
    public void onPreLogin(PreLoginEvent event) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reason));
    }

    /** A plugin after the first shut may allow the connection again; this one shuts it after every plugin. */
    @Subscribe(priority = Short.MIN_VALUE)
    public void onPreLoginAgain(PreLoginEvent event) {
        event.setResult(PreLoginEvent.PreLoginComponentResult.denied(reason));
    }

    /** A connection still let in, by a plugin that runs after this one, is turned away before it reaches a server. */
    @Subscribe(priority = Short.MIN_VALUE)
    public void onLogin(LoginEvent event) {
        event.setResult(ResultedEvent.ComponentResult.denied(reason));
    }
}
