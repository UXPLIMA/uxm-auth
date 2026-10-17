package com.uxplima.auth.velocity.adapter.inbound;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.domain.premium.Refusal;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import org.slf4j.Logger;

/**
 * Turns the door's answers into what Velocity does with a connection: online mode for a name the door asks,
 * offline mode for one it lets through, the profile the backends receive, and a refusal with its reason.
 *
 * <p>The door may wait on the database and the web, so every step that asks it runs as an asynchronous event
 * task, off the thread that reads the network. It runs late, after the plugins that decide a connection for
 * themselves: a connection already turned away, or already put in offline mode as Floodgate does a Bedrock
 * player, is left as they left it.
 */
public final class DoorListener {

    /** Below every plugin that leaves the default, so a decision another plugin made is seen. */
    private static final short LATE = -1000;

    private final ProxyDoor door;
    private final DoorWords words;
    private final Logger logger;

    public DoorListener(ProxyDoor door, DoorWords words, Logger logger) {
        this.door = Objects.requireNonNull(door, "door");
        this.words = Objects.requireNonNull(words, "words");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe(priority = LATE)
    public EventTask onPreLogin(PreLoginEvent event) {
        return EventTask.async(() -> preLogin(event));
    }

    @Subscribe(priority = LATE)
    public EventTask onProfile(GameProfileRequestEvent event) {
        return EventTask.async(() -> profile(event));
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        login(event);
    }

    @Subscribe
    public void onLeft(DisconnectEvent event) {
        left(event);
    }

    void preLogin(PreLoginEvent event) {
        PreLoginEvent.PreLoginComponentResult before = event.getResult();
        if (!before.isAllowed() || before.isForceOfflineMode()) {
            return;
        }
        String host = event.getConnection()
                .getVirtualHost()
                .map(InetSocketAddress::getHostString)
                .orElse("");
        try {
            event.setResult(
                    switch (door.preLogin(
                            event.getUsername(), event.getConnection().getRemoteAddress(), host)) {
                        case ProxyDoor.Gate.Online online -> PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                        case ProxyDoor.Gate.Offline offline -> PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
                        case ProxyDoor.Gate.Refused refused ->
                            PreLoginEvent.PreLoginComponentResult.denied(
                                    words.refusal(refused.why(), refused.movedTo()));
                        case ProxyDoor.Gate.BadName bad ->
                            PreLoginEvent.PreLoginComponentResult.denied(words.name(bad.verdict()));
                    });
        } catch (RuntimeException failure) {
            // Nobody is let through a door that cannot answer: the name might be a paid owner's.
            logger.warn(
                    "Could not decide how " + event.getUsername() + " joins; the connection is turned away.", failure);
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    words.refusal(Refusal.LOOKUP_FAILED, Optional.empty())));
        }
    }

    void profile(GameProfileRequestEvent event) {
        event.setGameProfile(door.profile(
                event.getGameProfile(),
                event.isOnlineMode(),
                event.getConnection().getRemoteAddress()));
    }

    void login(LoginEvent event) {
        door.arrival(event.getPlayer().getRemoteAddress())
                .filter(ProxyDoor.Arrival.Refused.class::isInstance)
                .map(ProxyDoor.Arrival.Refused.class::cast)
                .ifPresent(refused -> event.setResult(
                        ResultedEvent.ComponentResult.denied(words.refusal(refused.why(), Optional.empty()))));
    }

    void left(DisconnectEvent event) {
        door.gone(event.getPlayer().getRemoteAddress());
    }

    ProxyDoor door() {
        return door;
    }
}
