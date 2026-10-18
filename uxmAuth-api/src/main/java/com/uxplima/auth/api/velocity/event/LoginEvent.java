package com.uxplima.auth.api.velocity.event;

import java.util.Objects;

import com.uxplima.auth.api.LoginWay;
import com.velocitypowered.api.proxy.Player;

/** A player is logged in at the proxy, by any way. */
public record LoginEvent(Player player, LoginWay way) {
    public LoginEvent {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(way, "way");
    }
}
