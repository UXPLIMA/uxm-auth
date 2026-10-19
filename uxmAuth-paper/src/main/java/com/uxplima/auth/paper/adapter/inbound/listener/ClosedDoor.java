package com.uxplima.auth.paper.adapter.inbound.listener;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Keeps every connection out while the console moves the accounts, so nobody writes what the copy would miss.
 * It runs before the door reads an account, and the door passes over a connection already turned away.
 */
public final class ClosedDoor implements Listener {

    private final BooleanSupplier closed;
    private final Messages messages;

    /** @param closed whether a move of the accounts runs now */
    public ClosedDoor(BooleanSupplier closed, Messages messages) {
        this.closed = Objects.requireNonNull(closed, "closed");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED && closed.getAsBoolean()) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    messages.render(Audience.empty(), Keys.DATABASE_DOOR_SHUT));
        }
    }
}
