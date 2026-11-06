package com.uxplima.auth.paper.adapter.outbound.network;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.paper.adapter.inbound.hold.Reports;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Tells the proxy, through the player's own connection, what the door here did: signed with the reports' key of
 * the network's secret and naming this server, so the proxy takes it only from a server that holds the secret, and
 * only on a connection to the server it names.
 *
 * <p>The proxy opens the channel on the player's connection, and the player's client can close it again. Without
 * the channel the proxy never hears that a player waits again, after a logout or a password reset here, and would
 * write the next server a claim that they are logged in. So a player whose client closes the channel, or whose
 * report that they wait cannot reach the proxy for long, is sent away instead.
 */
public final class ChannelReports implements Reports, Listener {

    /** Far longer than the proxy takes to open the channel after a player joins. */
    private static final Duration UNDELIVERED = Duration.ofSeconds(10);

    private final Plugin plugin;
    private final Supplier<Optional<Claims>> claims;
    private final Supplier<String> here;
    private final Clock clock;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Map<UUID, Claim.Standing> waiting = new ConcurrentHashMap<>();

    /** Whether the proxy opened the channel on any player's connection since the start: it is ours, then. */
    private final AtomicBoolean proxyOpens = new AtomicBoolean();

    /**
     * @param claims the signer, or empty while this server stands alone
     * @param here this server's name in the proxy's list, which every report names, so the proxy takes it only on
     *     a connection to this server
     */
    public ChannelReports(
            Plugin plugin,
            Supplier<Optional<Claims>> claims,
            Supplier<String> here,
            Clock clock,
            Scheduler scheduler,
            Messages messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.claims = Objects.requireNonNull(claims, "claims");
        this.here = Objects.requireNonNull(here, "here");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

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

    /**
     * The player's connection closed a channel. Only the proxy opens the report channel and nothing closes it, so
     * a client that closes it is hiding what this server reports, and is sent away while the network is on.
     */
    public void closed(Player player, String channel) {
        Objects.requireNonNull(player, "player");
        if (Claims.CHANNEL.equals(channel) && claims.get().isPresent()) {
            cutOff(player, Keys.KICK_NETWORK_CHANNEL);
        }
    }

    /** The player left: a report that still waited goes nowhere. */
    public void left(Player player) {
        waiting.computeIfPresent(Objects.requireNonNull(player, "player").getUniqueId(), (id, standing) -> null);
    }

    @EventHandler
    public void onChannel(PlayerRegisterChannelEvent event) {
        if (Claims.CHANNEL.equals(event.getChannel())) {
            proxyOpens.set(true);
        }
        opened(event.getPlayer());
    }

    @EventHandler
    public void onChannelClosed(PlayerUnregisterChannelEvent event) {
        closed(event.getPlayer(), event.getChannel());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        left(event.getPlayer());
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
     * moment after the join: a report made before that waits, and only the latest one is kept. A report that the
     * player waits and that still has not gone after a while sends the player away rather than wait for ever.
     */
    private void send(Player player, Claim.Standing standing) {
        Objects.requireNonNull(player, "player");
        if (claims.get().isPresent() && !player.getListeningPluginChannels().contains(Claims.CHANNEL)) {
            waiting.compute(player.getUniqueId(), (id, earlier) -> standing);
            if (standing instanceof Claim.Waiting) {
                TaskHandle unused = scheduler.entityLater(player, UNDELIVERED, () -> undelivered(player));
            }
            return;
        }
        claims.get()
                .ifPresent(signer -> player.sendPluginMessage(
                        plugin,
                        Claims.CHANNEL,
                        signer.sign(
                                        new Claim(
                                                player.getUniqueId(),
                                                player.getName(),
                                                here.get(),
                                                standing,
                                                clock.instant(),
                                                UUID.randomUUID().toString()),
                                        Claim.Direction.TO_PROXY)
                                .getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * The report that the player waits has not reached the proxy: the player is sent away. Only behind a proxy that
     * opens the channel at all: one that never does, such as a proxy in online mode, hears no report from anybody.
     */
    private void undelivered(Player player) {
        if (proxyOpens.get() && player.isOnline() && waiting.get(player.getUniqueId()) instanceof Claim.Waiting) {
            waiting.remove(player.getUniqueId());
            player.kick(messages.render(player, Keys.KICK_NETWORK_CLAIM));
        }
    }

    private void cutOff(Player player, Keys why) {
        TaskHandle unused = scheduler.entity(player, () -> {
            if (player.isOnline()) {
                player.kick(messages.render(player, why));
            }
        });
    }
}
