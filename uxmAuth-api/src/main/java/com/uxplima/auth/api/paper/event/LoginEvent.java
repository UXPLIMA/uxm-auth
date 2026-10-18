package com.uxplima.auth.api.paper.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import com.uxplima.auth.api.LoginWay;

/** A player is logged in on this server, by any way. Fired on the player's own thread. */
public final class LoginEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LoginWay way;

    public LoginEvent(Player player, LoginWay way) {
        super(Objects.requireNonNull(player, "player"));
        this.way = Objects.requireNonNull(way, "way");
    }

    public LoginWay way() {
        return way;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
