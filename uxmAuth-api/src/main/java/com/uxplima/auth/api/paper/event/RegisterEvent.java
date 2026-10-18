package com.uxplima.auth.api.paper.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** A player registered on this server and is logged in. Fired on the player's own thread. */
public final class RegisterEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    public RegisterEvent(Player player) {
        super(Objects.requireNonNull(player, "player"));
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
