package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Objects;

import com.uxplima.auth.adapter.outbound.AuthSettings.Moment;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;

/** A login runs the list every login runs, then the list of the way the player came in. */
public final class MomentsByMethod {

    private MomentsByMethod() {}

    /** Runs every moment through {@code runner}, a login followed by its way's own list. */
    public static MomentRunner of(MomentRunner runner, Sessions sessions) {
        Objects.requireNonNull(runner, "runner");
        Objects.requireNonNull(sessions, "sessions");
        return (player, moment) -> {
            runner.fire(player, moment);
            if (moment != Moment.LOGGED_IN) {
                return;
            }
            sessions.state(player.getUniqueId())
                    .filter(AuthState.Authenticated.class::isInstance)
                    .flatMap(state -> Moment.afterLogin(((AuthState.Authenticated) state).method()))
                    .ifPresent(way -> runner.fire(player, way));
        };
    }
}
