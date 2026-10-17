package com.uxplima.auth.paper.adapter.inbound.hold;

import org.bukkit.entity.Player;

import com.uxplima.auth.domain.state.AuthMethod;

/** What the door tells the proxy of a network, so every server agrees on who is logged in. */
public interface Reports {

    /** A server that stands alone tells nobody. */
    Reports NONE = new Reports() {
        @Override
        public void loggedIn(Player player, AuthMethod method) {}

        @Override
        public void waiting(Player player) {}
    };

    /** The player logged in here, by this method. */
    void loggedIn(Player player, AuthMethod method);

    /** The player waits here for a password. */
    void waiting(Player player);
}
