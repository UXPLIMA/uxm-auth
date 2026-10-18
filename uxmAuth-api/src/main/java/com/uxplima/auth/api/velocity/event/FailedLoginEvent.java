package com.uxplima.auth.api.velocity.event;

import java.util.Objects;

import com.velocitypowered.api.proxy.Player;

/**
 * A player answered the login at the proxy with a wrong password.
 *
 * @param attemptsLeft how many more tries the player has before they are sent away
 */
public record FailedLoginEvent(Player player, int attemptsLeft) {
    public FailedLoginEvent {
        Objects.requireNonNull(player, "player");
    }
}
