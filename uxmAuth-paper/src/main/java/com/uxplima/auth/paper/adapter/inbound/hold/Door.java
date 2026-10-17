package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthMethod;
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
    private final Greeter greeter;
    private final ScreenOffer screens;
    private final Welcomes welcomes;
    private final Notices notices;
    private final Reports reports;
    private final Remembering remembering;

    public Door(
            Sessions sessions,
            HoldKeeper keeper,
            WaitingClock clock,
            MomentRunner moments,
            Greeter greeter,
            ScreenOffer screens,
            Welcomes welcomes,
            Notices notices,
            Reports reports,
            Remembering remembering) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.moments = Objects.requireNonNull(moments, "moments");
        this.greeter = Objects.requireNonNull(greeter, "greeter");
        this.screens = Objects.requireNonNull(screens, "screens");
        this.welcomes = Objects.requireNonNull(welcomes, "welcomes");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.reports = Objects.requireNonNull(reports, "reports");
        this.remembering = Objects.requireNonNull(remembering, "remembering");
    }

    /** A player arrived, or logged out: anything a crash left is given back, and a waiting player is held. */
    public void hold(Player player) {
        Objects.requireNonNull(player, "player");
        keeper.arrive(player);
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (state instanceof AuthState.Authenticated in) {
            arrivedIn(player, in);
            return;
        }
        if (state == null) {
            return;
        }
        reports.waiting(player);
        notices.deliver(player, false);
        clock.start(player);
        screens.offer(player, state);
        greeter.greet(
                player,
                state instanceof AuthState.Unregistered
                        ? AuthSettings.Moment.JOIN_UNREGISTERED
                        : AuthSettings.Moment.JOIN_REGISTERED);
    }

    /** A player who arrives already let in: told to the proxy, remembered, and greeted. */
    private void arrivedIn(Player player, AuthState.Authenticated in) {
        if (in.method() != AuthMethod.NETWORK) {
            // A login the network vouched for came from the proxy, which already knows it.
            reports.loggedIn(player, in.method());
        }
        remembering.loggedIn(player, in);
        // Let in before the world, by the dialog, by a session, by Floodgate or by Mojang: the way-in moment
        // waited for a world.
        welcomes.take(player.getUniqueId())
                .or(() -> in.method() == AuthMethod.BEDROCK
                                || in.method() == AuthMethod.PREMIUM
                                || in.method() == AuthMethod.SESSION
                        ? Optional.of(AuthSettings.Moment.LOGGED_IN)
                        : Optional.empty())
                .ifPresent(moment -> greeter.welcome(player, moment));
        notices.deliver(player, true);
    }

    /** The player left: a greeting and the notices that waited for them are dropped. */
    public void leave(Player player) {
        greeter.forget(player);
        notices.forget(player.getUniqueId());
    }

    /** The client said what language it reads, so a greeting that waited for it can run. */
    public void languageKnown(Player player) {
        greeter.languageKnown(player);
    }

    /** A player got in: everything the hold took comes back, and the moment of their way in runs. */
    public void open(Player player, AuthSettings.Moment moment) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(moment, "moment");
        greeter.forget(player);
        keeper.giveBack(player);
        sessions.state(player.getUniqueId())
                .filter(AuthState.Authenticated.class::isInstance)
                .map(AuthState.Authenticated.class::cast)
                .ifPresent(in -> {
                    reports.loggedIn(player, in.method());
                    remembering.loggedIn(player, in);
                });
        moments.fire(player, moment);
        notices.deliver(player, true);
    }
}
