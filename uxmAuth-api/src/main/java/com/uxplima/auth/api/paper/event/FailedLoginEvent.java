package com.uxplima.auth.api.paper.event;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** A player answered the login with a wrong password. Fired off the server's threads. */
public final class FailedLoginEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final int attemptsLeft;

    public FailedLoginEvent(Player player, int attemptsLeft) {
        super(Objects.requireNonNull(player, "player"), true);
        this.attemptsLeft = attemptsLeft;
    }

    /** How many more tries the player has before they are sent away. */
    public int attemptsLeft() {
        return attemptsLeft;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
