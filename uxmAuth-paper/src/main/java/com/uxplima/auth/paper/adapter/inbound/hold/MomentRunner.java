package com.uxplima.auth.paper.adapter.inbound.hold;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;

/** Runs the effect list the operator wrote for one moment, for one player. */
@FunctionalInterface
public interface MomentRunner {

    void fire(Player player, AuthSettings.Moment moment);
}
