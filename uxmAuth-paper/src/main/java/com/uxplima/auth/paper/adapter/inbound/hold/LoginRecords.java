package com.uxplima.auth.paper.adapter.inbound.hold;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Every login that lands on this server, written to the history off the player's thread. On a network the proxy
 * hands the first server the way the player came in and every later server {@link AuthMethod#NETWORK}, so a login
 * is written once, where it first landed. A write that fails is told in the console and never reaches the login.
 */
public final class LoginRecords {

    private final LoginHistory history;
    private final Consumer<Runnable> async;
    private final Clock clock;
    private final Consumer<String> warn;

    /** @param async runs a task off every server and region thread */
    public LoginRecords(LoginHistory history, Consumer<Runnable> async, Clock clock, Consumer<String> warn) {
        this.history = Objects.requireNonNull(history, "history");
        this.async = Objects.requireNonNull(async, "async");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** The player is in by this login. */
    public void loggedIn(Player player, AuthState.Authenticated in) {
        Objects.requireNonNull(player, "player");
        if (Objects.requireNonNull(in, "in").method() == AuthMethod.NETWORK) {
            return;
        }
        InetSocketAddress from = player.getAddress();
        String address = from == null || from.getAddress() == null
                ? ""
                : from.getAddress().getHostAddress();
        UUID account = in.account().id();
        LoginHistory.Entry entry = new LoginHistory.Entry(clock.instant(), address, in.method());
        async.accept(() -> {
            try {
                history.record(account, entry);
            } catch (RuntimeException failed) {
                warn.accept("A login could not be written to the history: " + failed.getMessage());
            }
        });
    }

    /** Drops every login older than the window the operator keeps. Runs on the caller's thread. */
    public void prune(Duration keep) {
        history.prune(clock.instant().minus(Objects.requireNonNull(keep, "keep")));
    }
}
