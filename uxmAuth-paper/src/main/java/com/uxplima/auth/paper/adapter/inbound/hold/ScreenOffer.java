package com.uxplima.auth.paper.adapter.inbound.hold;

import org.bukkit.entity.Player;

import com.uxplima.auth.domain.state.AuthState;

/** What the door shows a player it holds, beside the commands. */
@FunctionalInterface
public interface ScreenOffer {

    /** Nothing beyond the commands. */
    ScreenOffer NONE = (player, state) -> {};

    void offer(Player player, AuthState state);
}
