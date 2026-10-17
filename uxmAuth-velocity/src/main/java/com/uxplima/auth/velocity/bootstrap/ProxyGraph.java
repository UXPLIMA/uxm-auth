package com.uxplima.auth.velocity.bootstrap;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.SqlLastServers;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.port.LastServers;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.adapter.inbound.ClaimWriter;
import com.uxplima.auth.velocity.adapter.inbound.DoorListener;
import com.uxplima.auth.velocity.adapter.inbound.HoldListener;
import com.uxplima.auth.velocity.adapter.inbound.ProxyDoor;
import com.uxplima.auth.velocity.adapter.inbound.ProxySessions;
import com.uxplima.auth.velocity.adapter.inbound.ReportListener;
import com.uxplima.auth.velocity.adapter.inbound.RouteListener;
import com.uxplima.auth.velocity.adapter.outbound.ProxySecret;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

/**
 * The network half of the proxy: the identity decision before every connection, the hold until login, the
 * routes, the claims written for the servers and the reports read from them. Built by {@link ProxyModule} once
 * the files, the database and the words are in place.
 */
final class ProxyGraph implements AutoCloseable {

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private final HttpClient web = HttpClient.newHttpClient();
    private final WaitingWorld waiting;

    ProxyGraph(Object plugin, ProxyServer proxy, Logger logger, Path folder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.waiting = new WaitingWorld(plugin, proxy, logger, folder);
    }

    /**
     * Put the identity decision before every connection, unless the proxy is in online mode: then Velocity asks
     * Mojang for every player itself, and a cracked player could never join.
     */
    void open(PluginSettings settings, Database database, Messages words) {
        if (proxy.getConfiguration().isOnlineMode()) {
            logger.warn("online-mode is on in velocity.toml, so the proxy proves every player itself. Premium"
                    + " auto-login stays off; turn online-mode off to let uxmAuth ask paid accounts alone.");
            return;
        }
        Clock clock = Clock.systemUTC();
        SqlAccountRepository accounts = new SqlAccountRepository(database);
        SqlDisputes disputes = new SqlDisputes(database);
        UuidScheme scheme = settings.current().ownership().uuidScheme();
        AtTheDoor atTheDoor = atTheDoor(settings, accounts, disputes, clock);
        ProxyDoor door = new ProxyDoor(
                atTheDoor,
                new LetPremiumIn(
                        accounts,
                        disputes,
                        () -> settings.current().ownership().conflict(),
                        () -> settings.current().ownership().renameMark(),
                        scheme,
                        clock),
                new Moves(accounts),
                new ReadTheName(
                        accounts,
                        () -> settings.current().namePolicy(),
                        () -> settings.current().passwords().maxAttempts()),
                () -> settings.current().ownership().crackedMark(),
                scheme,
                clock);
        ProxySessions sessions = listen(door, settings, words, clock, new SqlLastServers(database, clock));
        waiting.open(settings, accounts, words, sessions);
        logger.info("Premium auto-login is on: paid accounts are proved by Mojang at the proxy.");
    }

    /**
     * The door before every connection, the hold until login, the claim before every server, and the servers'
     * reports after.
     */
    private ProxySessions listen(
            ProxyDoor door, PluginSettings settings, Messages words, Clock clock, LastServers lastServers) {
        ProxySessions sessions = new ProxySessions();
        Supplier<Optional<Claims>> claims = claims(settings);
        RouteListener routes = new RouteListener(
                sessions,
                () -> settings.current().network().routing(),
                lastServers,
                proxy::getServer,
                this::playersOnline,
                RandomGenerator.getDefault());
        register(new DoorListener(door, sessions, new DoorWords(words), logger));
        register(new HoldListener(
                sessions,
                proxy.getCommandManager(),
                () -> settings.current().network().allowedCommands(),
                routes::waitingRoom,
                words));
        register(routes);
        register(new ClaimWriter(sessions, claims, clock));
        proxy.getChannelRegistrar().register(ReportListener.CHANNEL);
        register(new ReportListener(sessions, claims, clock, logger, routes::loggedIn));
        return sessions;
    }

    @Override
    public void close() {
        waiting.close();
    }

    private void register(Object listener) {
        proxy.getEventManager().register(plugin, listener);
    }

    /** The players on each server of the proxy's list, by name. */
    private Map<String, Integer> playersOnline() {
        Map<String, Integer> online = new HashMap<>();
        for (RegisteredServer server : proxy.getAllServers()) {
            online.put(
                    server.getServerInfo().getName(),
                    server.getPlayersConnected().size());
        }
        return online;
    }

    /**
     * The signer of the claims every server behind this proxy reads. The secret is the one in config.conf, or the
     * one this proxy made on its first start, which the console points the operator to.
     */
    private Supplier<Optional<Claims>> claims(PluginSettings settings) {
        ProxySecret.Loaded loaded =
                ProxySecret.loadOrCreate(folder, settings.current().network().secret());
        if (loaded.created()) {
            logger.info(
                    "This proxy made the network secret and wrote it to {}. Copy it into network.secret in"
                            + " config.conf on every server behind this proxy, and set network.enabled to true there.",
                    loaded.file());
        }
        NetworkSecret secret = loaded.secret();
        return () -> Optional.of(new Claims(secret, settings.current().network().claimFor()));
    }

    /** The decision at the pre-login: the web sources, the operator's rule, and the memories of claims and failures. */
    private AtTheDoor atTheDoor(
            PluginSettings settings, SqlAccountRepository accounts, SqlDisputes disputes, Clock clock) {
        return new AtTheDoor(
                accounts,
                () -> settings.current().namePolicy(),
                new PremiumLookup(
                        new PremiumSources(web, () -> settings.current().premium(), clock)::ordered,
                        () -> settings.current().premium().keepAnswers(),
                        clock),
                () -> settings.current().ownership().decision(),
                new NameMemory(() -> settings.current().ownership().failedMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                disputes);
    }
}
