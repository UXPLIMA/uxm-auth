package com.uxplima.auth.paper.adapter.outbound.network;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.Reports;

/**
 * Tells the proxy, through the player's own connection, what the door here did: signed with the network's
 * secret, so the proxy takes it only from a server that holds the secret.
 */
public final class ChannelReports implements Reports, Listener {

    private final Plugin plugin;
    private final Supplier<Optional<Claims>> claims;
    private final Clock clock;
    private final Map<UUID, Claim.Standing> waiting = new ConcurrentHashMap<>();

    /** The player's connection opened a channel: a report that waited goes now, if the channel was ours. */
    public void opened(Player player) {
        Objects.requireNonNull(player, "player");
        Claim.Standing[] taken = new Claim.Standing[1];
        waiting.computeIfPresent(player.getUniqueId(), (id, standing) -> {
            taken[0] = standing;
            return null;
        });
        if (taken[0] != null) {
            send(player, taken[0]);
        }
    }

    /** The player left: a report that still waited goes nowhere. */
    public void left(Player player) {
        waiting.computeIfPresent(Objects.requireNonNull(player, "player").getUniqueId(), (id, standing) -> null);
    }

    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent event) {
        opened(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        left(event.getPlayer());
    }

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

    /**
     * A server may only write on a channel the proxy opened on the player's connection, and the proxy opens it a
     * moment after the join: a report made before that waits, and only the latest one is kept.
     */
    private void send(Player player, Claim.Standing standing) {
        Objects.requireNonNull(player, "player");
        if (claims.get().isPresent() && !player.getListeningPluginChannels().contains(Claims.CHANNEL)) {
            waiting.compute(player.getUniqueId(), (id, earlier) -> standing);
            return;
        }
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
