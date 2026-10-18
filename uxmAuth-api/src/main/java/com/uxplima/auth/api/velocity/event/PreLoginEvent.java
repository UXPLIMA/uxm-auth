package com.uxplima.auth.api.velocity.event;

import java.util.Objects;
import java.util.Optional;

import com.velocitypowered.api.proxy.Player;
import org.jspecify.annotations.Nullable;

/** Fired as a player answers the login at the proxy, before the password is checked. Refused, the login is refused with the reason, and no wrong password is counted. */
public final class PreLoginEvent {

    private final Player player;
    private volatile @Nullable String refusal;

    public PreLoginEvent(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public Player player() {
        return player;
    }

    /** Refuses the login, and the player reads this reason. */
    public void refuse(String reason) {
        this.refusal = Objects.requireNonNull(reason, "reason");
    }

    /** Why the login is refused, when a listener refused it. */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }
}
