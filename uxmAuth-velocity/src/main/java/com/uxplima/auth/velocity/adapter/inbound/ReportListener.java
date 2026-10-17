package com.uxplima.auth.velocity.adapter.inbound;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

/**
 * What a server tells the proxy on the report channel: a login it let in, or a player it holds to wait. It is
 * taken only from a server connection, about the player on that connection, signed with the network's secret.
 * The channel is never passed on, to a client or to a server, whoever sent on it.
 */
public final class ReportListener {

    /** The channel the servers report on. */
    public static final MinecraftChannelIdentifier CHANNEL = MinecraftChannelIdentifier.from(Claims.CHANNEL);

    private final ProxySessions sessions;
    private final Supplier<Optional<Claims>> claims;
    private final Clock clock;
    private final Logger logger;

    public ReportListener(ProxySessions sessions, Supplier<Optional<Claims>> claims, Clock clock, Logger logger) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Subscribe
    public void onMessage(PluginMessageEvent event) {
        read(event);
    }

    void read(PluginMessageEvent event) {
        if (!event.getIdentifier().getId().equals(Claims.CHANNEL)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection server)) {
            // A client could write anything here; only a server holds the secret, and only a server is listened to.
            return;
        }
        Player player = server.getPlayer();
        Optional<Claims> reader = claims.get();
        if (reader.isEmpty()) {
            return;
        }
        String text = new String(event.getData(), StandardCharsets.UTF_8);
        switch (reader.get().read(text, player.getUniqueId(), player.getUsername(), clock.instant())) {
            case Claims.Reading.Valid valid -> {
                switch (valid.claim().standing()) {
                    case Claim.LoggedIn in -> sessions.reported(player.getUniqueId(), in.method());
                    // The server knows no door flags of the proxy's; the proxy keeps its own.
                    case Claim.Waiting waiting -> sessions.loggedOut(player.getUniqueId());
                }
            }
            case Claims.Reading.Refused refused ->
                logger.warn(
                        "A server's report about {} was refused ({}). Check that network.secret is the same on the"
                                + " proxy and on every server, and that the clocks agree.",
                        DoorListener.printable(player.getUsername()),
                        refused.why().name());
        }
    }
}
