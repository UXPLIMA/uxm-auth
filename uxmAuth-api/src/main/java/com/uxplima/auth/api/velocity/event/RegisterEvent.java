package com.uxplima.auth.api.velocity.event;

import java.util.Objects;

import com.velocitypowered.api.proxy.Player;

/** A player registered at the proxy and is logged in. */
public record RegisterEvent(Player player) {
    public RegisterEvent {
        Objects.requireNonNull(player, "player");
    }
}
