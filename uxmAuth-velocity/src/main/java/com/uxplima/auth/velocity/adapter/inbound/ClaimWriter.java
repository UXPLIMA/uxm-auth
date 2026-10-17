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
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;

/**
 * Writes a fresh claim into the profile the proxy forwards, before every connection to a server. The server
 * reads it at its pre-login and takes it off the profile there, so no client ever sees it.
 */
public final class ClaimWriter {

    /** The profile property the claim travels in. */
    public static final String PROPERTY = "uxmauth";

    /** Below every plugin that leaves the default, so the server another plugin picked is the one written for. */
    private static final short LATE = -1000;

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
            claim(event.getPlayer());
        }
    }

    /** One claim on the player's profile: this connection's, or none for a player the proxy does not know. */
    void claim(Player player) {
        List<GameProfile.Property> properties = new ArrayList<>();
        for (GameProfile.Property property : player.getGameProfileProperties()) {
            if (!property.getName().equals(PROPERTY)) {
                properties.add(property);
            }
        }
        Optional<Claims> signer = claims.get();
        Optional<Claim.Standing> standing = sessions.nextStanding(player.getUniqueId());
        if (signer.isPresent() && standing.isPresent()) {
            Claim claim = new Claim(
                    player.getUniqueId(),
                    player.getUsername(),
                    standing.get(),
                    clock.instant(),
                    UUID.randomUUID().toString());
            properties.add(new GameProfile.Property(PROPERTY, signer.get().sign(claim), ""));
        }
        player.setGameProfileProperties(properties);
    }
}
