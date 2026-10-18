package com.uxplima.auth.paper.adapter.inbound.hold;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.factor.FactorStates;
import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Every login that lands on this server, written to the history off the player's thread. On a network the proxy
 * hands the first server the way the player came in and every later server {@link AuthMethod#NETWORK}, so a login
 * is written once, where it first landed. A write that fails is told in the console and never reaches the login.
 * Every login here, the network's too, also has its second factor read, so a placeholder answers it from memory
 * while the player stays.
 */
public final class LoginRecords {

    /** Who hears of a login off the player's thread, before it is written. */
    @FunctionalInterface
    public interface Landed {
        void landed(UUID account, PlayerName name, String address);
    }

    private final LoginHistory history;
    private final Consumer<Runnable> async;
    private final Clock clock;
    private final Consumer<String> warn;
    private final Landed landed;
    private final @Nullable FactorStates factors;

    /** @param async runs a task off every server and region thread */
    public LoginRecords(LoginHistory history, Consumer<Runnable> async, Clock clock, Consumer<String> warn) {
        this(history, async, clock, warn, (account, name, address) -> {});
    }

    /** @param landed hears of each login first, which is the alerts */
    public LoginRecords(
            LoginHistory history, Consumer<Runnable> async, Clock clock, Consumer<String> warn, Landed landed) {
        this(history, async, clock, warn, landed, null);
    }

    /** @param factors what is known of each account's second factor while its player is here */
    public LoginRecords(
            LoginHistory history,
            Consumer<Runnable> async,
            Clock clock,
            Consumer<String> warn,
            Landed landed,
            @Nullable FactorStates factors) {
        this.factors = factors;
        this.history = Objects.requireNonNull(history, "history");
        this.async = Objects.requireNonNull(async, "async");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warn = Objects.requireNonNull(warn, "warn");
        this.landed = Objects.requireNonNull(landed, "landed");
    }

    /** The player is in by this login. */
    public void loggedIn(Player player, AuthState.Authenticated in) {
        Objects.requireNonNull(player, "player");
        FactorStates known = factors;
        if (known != null) {
            UUID id = Objects.requireNonNull(in, "in").account().id();
            async.accept(() -> readFactors(known, id));
        }
        if (Objects.requireNonNull(in, "in").method() == AuthMethod.NETWORK) {
            return;
        }
        InetSocketAddress from = player.getAddress();
        String address = from == null || from.getAddress() == null
                ? ""
                : from.getAddress().getHostAddress();
        UUID account = in.account().id();
        PlayerName name = in.account().name();
        LoginHistory.Entry entry = new LoginHistory.Entry(clock.instant(), address, in.method());
        async.accept(() -> {
            try {
                landed.landed(account, name, address);
            } catch (RuntimeException failed) {
                warn.accept("A login could not be told to the alerts: " + failed.getMessage());
            }
            try {
                history.record(account, entry);
            } catch (RuntimeException failed) {
                warn.accept("A login could not be written to the history: " + failed.getMessage());
            }
        });
    }

    private void readFactors(FactorStates known, UUID account) {
        try {
            known.refresh(account);
        } catch (RuntimeException failed) {
            warn.accept("A second factor could not be read for a placeholder: " + failed.getMessage());
        }
    }

    /** The player of this login left: what was kept of their account for the placeholders goes. */
    public void left(AuthState.Authenticated in) {
        FactorStates known = factors;
        if (known != null) {
            known.forget(Objects.requireNonNull(in, "in").account().id());
        }
    }

    /** Drops every login older than the window the operator keeps. Runs on the caller's thread. */
    public void prune(Duration keep) {
        history.prune(clock.instant().minus(Objects.requireNonNull(keep, "keep")));
    }
}
