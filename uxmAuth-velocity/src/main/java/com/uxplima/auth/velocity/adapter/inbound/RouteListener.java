package com.uxplima.auth.velocity.adapter.inbound;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.port.LastServers;
import com.uxplima.auth.domain.network.Destinations;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

/**
 * Where the proxy sends a player. A player who waits goes first to an auth server, when the operator names
 * any; a player Mojang logged in goes first where the rule after login says, and so does a player who logs in
 * on an auth server. A player who logs in anywhere else stays where they are.
 *
 * <p>The server a logged-in player reaches is remembered for the last-server rule. The database is read and
 * written off the thread that reads the network.
 */
public final class RouteListener {

    private final ProxySessions sessions;
    private final Supplier<AuthSettings.Routing> routing;
    private final LastServers lastServers;
    private final Function<String, Optional<RegisteredServer>> servers;
    private final Supplier<Map<String, Integer>> online;
    private final RandomGenerator random;

    /**
     * @param servers the proxy's server of each name
     * @param online the players on each server that is up, by name
     */
    public RouteListener(
            ProxySessions sessions,
            Supplier<AuthSettings.Routing> routing,
            LastServers lastServers,
            Function<String, Optional<RegisteredServer>> servers,
            Supplier<Map<String, Integer>> online,
            RandomGenerator random) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.routing = Objects.requireNonNull(routing, "routing");
        this.lastServers = Objects.requireNonNull(lastServers, "lastServers");
        this.servers = Objects.requireNonNull(servers, "servers");
        this.online = Objects.requireNonNull(online, "online");
        this.random = Objects.requireNonNull(random, "random");
    }

    @Subscribe
    public EventTask onInitial(PlayerChooseInitialServerEvent event) {
        return EventTask.async(() -> initial(event));
    }

    @Subscribe
    public EventTask onConnected(ServerConnectedEvent event) {
        return EventTask.async(() -> connected(event));
    }

    void initial(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();
        AuthSettings.Routing now = routing.get();
        Optional<String> target = sessions.loggedIn(player.getUniqueId())
                ? afterLogin(player, now.destinations(random))
                : now.destinations(random).pick(now.authServers(), online.get());
        target.flatMap(servers).ifPresent(event::setInitialServer);
    }

    /** A server reported the player logged in: from an auth server they are sent on. */
    public void loggedIn(Player player) {
        Objects.requireNonNull(player, "player");
        AuthSettings.Routing now = routing.get();
        boolean waitingRoom = player.getCurrentServer()
                .map(connection -> now.authServers()
                        .contains(connection.getServer().getServerInfo().getName()))
                .orElse(false);
        if (waitingRoom) {
            afterLogin(player, now.destinations(random))
                    .flatMap(servers)
                    .ifPresent(server -> player.createConnectionRequest(server).fireAndForget());
        }
    }

    void connected(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String name = event.getServer().getServerInfo().getName();
        if (sessions.loggedIn(player.getUniqueId()) && !waitingRoom(name)) {
            lastServers.remember(player.getUniqueId(), name);
        }
    }

    /** Whether a waiting player may be sent to this server: it is one of the auth servers. */
    public boolean waitingRoom(String name) {
        return routing.get().authServers().contains(Objects.requireNonNull(name, "name"));
    }

    private Optional<String> afterLogin(Player player, Destinations destinations) {
        String host =
                player.getVirtualHost().map(InetSocketAddress::getHostString).orElse("");
        return destinations.afterLogin(host, lastServers.of(player.getUniqueId()), online.get());
    }
}
