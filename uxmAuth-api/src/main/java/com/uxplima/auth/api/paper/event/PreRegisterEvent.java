package com.uxplima.auth.api.paper.event;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

import org.jspecify.annotations.Nullable;

/** Fired as a player registers, before the account is made. Cancelled, the registration is refused with the reason. Fired off the server's threads. */
public final class PreRegisterEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private @Nullable String refusal;

    public PreRegisterEvent(Player player) {
        super(Objects.requireNonNull(player, "player"), true);
    }

    /** Refuses the registration, and the player reads this reason. */
    public void refuse(String reason) {
        this.refusal = Objects.requireNonNull(reason, "reason");
    }

    /** Why the registration is refused, when a listener refused it. */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }

    @Override
    public boolean isCancelled() {
        return refusal != null;
    }

    /** Cancelled without a reason, the player reads the plugin's own words. */
    @Override
    public void setCancelled(boolean cancel) {
        refusal = cancel ? Objects.requireNonNullElse(refusal, "") : null;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
