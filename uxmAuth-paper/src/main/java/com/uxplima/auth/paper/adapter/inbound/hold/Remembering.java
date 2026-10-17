package com.uxplima.auth.paper.adapter.inbound.hold;

import java.net.InetSocketAddress;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * Remembers a player who typed their password, from the address they came from, when the operator's rules say
 * so. A login by any other way was let in by something that is not a password, and is not written again. The
 * database is asked off the player's thread.
 */
public final class Remembering {

    private final Remember remember;
    private final Sessions sessions;
    private final Scheduler scheduler;

    /** @param sessions where the host the player typed at the door was kept */
    public Remembering(Remember remember, Sessions sessions, Scheduler scheduler) {
        this.remember = Objects.requireNonNull(remember, "remember");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** The player is in by this login. */
    public void loggedIn(Player player, AuthState.Authenticated in) {
        Objects.requireNonNull(player, "player");
        if (in.method() != AuthMethod.PASSWORD && in.method() != AuthMethod.REGISTERED) {
            return;
        }
        InetSocketAddress from = player.getAddress();
        if (from == null || from.getAddress() == null) {
            return;
        }
        String address = from.getAddress().getHostAddress();
        String host =
                sessions.held(player.getUniqueId()).map(Sessions.Held::host).orElse("");
        scheduler.async(() -> remember.loggedIn(in.account().id(), address, host));
    }
}
