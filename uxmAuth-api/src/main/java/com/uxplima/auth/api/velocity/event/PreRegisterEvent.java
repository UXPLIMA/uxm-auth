package com.uxplima.auth.api.velocity.event;

import java.util.Objects;
import java.util.Optional;

import com.velocitypowered.api.proxy.Player;
import org.jspecify.annotations.Nullable;

/** Fired as a player registers at the proxy, before the account is made. Refused, the registration is refused with the reason. */
public final class PreRegisterEvent {

    private final Player player;
    private volatile @Nullable String refusal;

    public PreRegisterEvent(Player player) {
        this.player = Objects.requireNonNull(player, "player");
    }

    public Player player() {
        return player;
    }

    /** Refuses the registration, and the player reads this reason. */
    public void refuse(String reason) {
        this.refusal = Objects.requireNonNull(reason, "reason");
    }

    /** Why the registration is refused, when a listener refused it. */
    public Optional<String> refusal() {
        return Optional.ofNullable(refusal);
    }
}
