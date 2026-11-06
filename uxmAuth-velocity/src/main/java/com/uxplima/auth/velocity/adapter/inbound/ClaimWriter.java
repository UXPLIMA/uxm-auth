package com.uxplima.auth.velocity.adapter.inbound;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;

/**
 * Writes a fresh claim into the profile the proxy forwards, before every connection to a server, for that server
 * alone. The server reads it at its pre-login and takes it off the profile there, and the proxy takes it off its
 * own copy once the connection is made or failed, so no client ever sees it.
 */
public final class ClaimWriter {

    /** The profile property the claim travels in. */
    public static final String PROPERTY = Claims.PROPERTY;

    /** Below every plugin that leaves the default, so the server another plugin picked is the one written for. */
    private static final short LATE = -1000;

    /** Above every plugin that leaves the default, so none of them reads a claim the server already took. */
    private static final short EARLY = 1000;

    private final ProxySessions sessions;
    private final Supplier<Optional<Claims>> claims;
    private final Clock clock;

    /** @param claims the signer, or empty while the proxy has no secret */
    public ClaimWriter(ProxySessions sessions, Supplier<Optional<Claims>> claims, Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Subscribe(priority = LATE)
    public void onPreConnect(ServerPreConnectEvent event) {
        if (event.getResult().isAllowed()) {
            claim(
                    event.getPlayer(),
                    event.getResult()
                            .getServer()
                            .map(server -> server.getServerInfo().getName())
                            .orElse(""));
        }
    }

    /**
     * The server took the profile, the claim with it: it comes off the proxy's copy before any other plugin reads
     * it, since a plugin that draws a tab list sends a player's profile to every other player.
     */
    @Subscribe(priority = EARLY)
    public void onConnected(ServerConnectedEvent event) {
        drop(event.getPlayer());
    }

    /** The connection failed: the claim written for it comes off the proxy's copy of the profile as well. */
    @Subscribe(priority = EARLY)
    public void onKicked(KickedFromServerEvent event) {
        drop(event.getPlayer());
    }

    /** The claim off the player's profile, when one is on it. */
    void drop(Player player) {
        List<GameProfile.Property> properties = without(player);
        if (properties.size() != player.getGameProfileProperties().size()) {
            player.setGameProfileProperties(properties);
        }
    }

    /**
     * One claim on the player's profile, written for the server the connection goes to: this connection's, or
     * none for a player the proxy does not know.
     */
    void claim(Player player, String server) {
        List<GameProfile.Property> properties = without(player);
        Optional<Claims> signer = claims.get();
        Optional<Claim.Standing> standing = sessions.nextStanding(player.getUniqueId());
        if (signer.isPresent() && standing.isPresent() && !server.isBlank()) {
            Claim claim = new Claim(
                    player.getUniqueId(),
                    player.getUsername(),
                    server,
                    standing.get(),
                    clock.instant(),
                    UUID.randomUUID().toString());
            properties.add(new GameProfile.Property(PROPERTY, signer.get().sign(claim, Claim.Direction.TO_SERVER), ""));
            sessions.wrote(player.getUniqueId(), claim.nonce());
        }
        player.setGameProfileProperties(properties);
    }

    /** The player's profile properties without a claim. */
    private static List<GameProfile.Property> without(Player player) {
        List<GameProfile.Property> properties = new ArrayList<>();
        for (GameProfile.Property property : player.getGameProfileProperties()) {
            if (!property.getName().equals(PROPERTY)) {
                properties.add(property);
            }
        }
        return properties;
    }
}
