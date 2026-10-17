package com.uxplima.auth.paper.adapter.outbound.network;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.Reports;

/**
 * Tells the proxy, through the player's own connection, what the door here did: signed with the network's
 * secret, so the proxy takes it only from a server that holds the secret.
 */
public final class ChannelReports implements Reports {

    private final Plugin plugin;
    private final Supplier<Optional<Claims>> claims;
    private final Clock clock;

    /** @param claims the signer, or empty while this server stands alone */
    public ChannelReports(Plugin plugin, Supplier<Optional<Claims>> claims, Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void loggedIn(Player player, AuthMethod method) {
        send(player, new Claim.LoggedIn(Objects.requireNonNull(method, "method")));
    }

    @Override
    public void waiting(Player player) {
        send(player, new Claim.Waiting(false, false, false));
    }

    private void send(Player player, Claim.Standing standing) {
        Objects.requireNonNull(player, "player");
        claims.get()
                .ifPresent(signer -> player.sendPluginMessage(
                        plugin,
                        Claims.CHANNEL,
                        signer.sign(new Claim(
                                        player.getUniqueId(),
                                        player.getName(),
                                        standing,
                                        clock.instant(),
                                        UUID.randomUUID().toString()))
                                .getBytes(StandardCharsets.UTF_8)));
    }
}
