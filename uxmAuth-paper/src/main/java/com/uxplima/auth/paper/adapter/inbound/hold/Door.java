package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;

/**
 * Where a player waits and how they are let in: the one place that holds a player, starts their clock and
 * greets them, and the one place that gives everything back and runs the moment of their way in.
 */
public final class Door {

    private final Sessions sessions;
    private final HoldKeeper keeper;
    private final WaitingClock clock;
    private final MomentRunner moments;

    public Door(Sessions sessions, HoldKeeper keeper, WaitingClock clock, MomentRunner moments) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.moments = Objects.requireNonNull(moments, "moments");
    }

    /** A player arrived, or logged out: anything a crash left is given back, and a waiting player is held. */
    public void hold(Player player) {
        Objects.requireNonNull(player, "player");
        keeper.arrive(player);
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (state == null || state instanceof AuthState.Authenticated) {
            return;
        }
        clock.start(player);
        moments.fire(
                player,
                state instanceof AuthState.Unregistered
                        ? AuthSettings.Moment.JOIN_UNREGISTERED
                        : AuthSettings.Moment.JOIN_REGISTERED);
    }

    /** A player got in: everything the hold took comes back, and the moment of their way in runs. */
    public void open(Player player, AuthSettings.Moment moment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(moment, "moment");
        keeper.giveBack(player);
        moments.fire(player, moment);
    }
}
