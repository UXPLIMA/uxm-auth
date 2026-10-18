package com.uxplima.auth.velocity.bootstrap;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.CommandFile;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.velocity.adapter.inbound.LimboLogin;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisit;
import com.uxplima.auth.velocity.adapter.inbound.ProxySessions;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboWait;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboWorld;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The proxy's waiting world, when the operator asks for it and LimboAPI is there to build it. LimboAPI's classes
 * are reached only past the check that the plugin is loaded, so a proxy without it never links them.
 */
final class WaitingWorld implements AutoCloseable {

    private static final String LIMBO_API = "limboapi";
    private static final String COMMANDS = "commands.conf";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private @Nullable HashingLane lane;

    WaitingWorld(Object plugin, ProxyServer proxy, Logger logger, Path folder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /** Hold every player who waits for the proxy's password in the world, when the settings ask for it. */
    void open(
            PluginSettings settings,
            SqlAccountRepository accounts,
            Messages words,
            ProxySessions network,
            Remember remember,
            LogIn.Failures failures) {
        if (settings.current().network().waitIn() != AuthSettings.WaitIn.LIMBO) {
            return;
        }
        Optional<?> limboApi = proxy.getPluginManager().getPlugin(LIMBO_API).flatMap(PluginContainer::getInstance);
        if (limboApi.isEmpty()) {
            logger.warn("network.wait-in is limbo, but LimboAPI is not on this proxy. Players who must log in wait"
                    + " on a server instead: on an auth server when network.auth-servers names one.");
            return;
        }
        try {
            hold(limboApi.get(), settings, accounts, words, network, remember, failures);
        } catch (RuntimeException | LinkageError foreign) {
            // A LimboAPI of another major version names its types elsewhere; the proxy starts without the world.
            close();
            logger.warn(
                    "The LimboAPI on this proxy is not one this uxmAuth speaks to: it needs LimboAPI 2, made for"
                            + " Velocity 4. Players who must log in wait on a server instead.",
                    foreign);
        }
    }

    private void hold(
            Object limboApi,
            PluginSettings settings,
            SqlAccountRepository accounts,
            Messages words,
            ProxySessions network,
            Remember remember,
            LogIn.Failures failures) {
        Path commands = folder.resolve(COMMANDS);
        Set<String> login = CommandFile.labels(commands, "login");
        Set<String> register = CommandFile.labels(commands, "register");
        LimboLogin passwords = passwords(settings, accounts, words, new Labels(login, register), failures);
        LimboWait wait = new LimboWait(
                LimboWorld.open(limboApi, folder, settings.current().network(), metas(login, register), logger),
                network::waitsAtTheProxy,
                (player, exit) -> visit(passwords, network, remember, player, exit),
                () -> settings.current().timeouts(),
                player -> words.render(player, Keys.KICK_TIMEOUT),
                logger);
        proxy.getEventManager().register(plugin, wait);
        logger.info("Players who must log in wait in the proxy's own world, built with LimboAPI.");
    }

    /** The labels of /login and /register, as commands.conf names them. */
    private record Labels(Set<String> login, Set<String> register) {}

    private LimboLogin passwords(
            PluginSettings settings,
            SqlAccountRepository accounts,
            Messages words,
            Labels labels,
            LogIn.Failures failures) {
        PluginSettings.Snapshot boot = settings.current();
        AuthSettings.Lane sizes = boot.lane();
        HashingLane hashingLane = new HashingLane(sizes.threads(), sizes.maxThreads(), sizes.queue());
        this.lane = hashingLane;
        Hashing hashing = boot.hashers();
        Sessions sessions = new Sessions();
        Clock clock = Clock.systemUTC();
        return new LimboLogin(
                new ReadTheName(
                        accounts,
                        () -> settings.current().namePolicy(),
                        () -> settings.current().passwords().maxAttempts()),
                new LogIn(accounts, hashing, hashingLane, sessions, clock, failures),
                new RegisterAccount(
                        accounts,
                        hashing,
                        hashingLane,
                        sessions,
                        () -> settings.current().passwordPolicy(),
                        clock),
                sessions,
                words,
                labels::login,
                labels::register,
                () -> settings.current().passwords().confirmOnRegister());
    }

    private static LimboVisit visit(
            LimboLogin passwords, ProxySessions network, Remember remember, Player player, LimboVisit.Exit exit) {
        return new LimboVisit(
                passwords,
                network,
                remember,
                player.getUniqueId(),
                new PlayerName(player.getUsername()),
                player.getRemoteAddress().getAddress().getHostAddress(),
                player.getVirtualHost().map(InetSocketAddress::getHostString).orElse(""),
                player,
                exit);
    }

    /** /login and /register as the client must know them to send them, in every word commands.conf gives. */
    private List<CommandMeta> metas(Set<String> login, Set<String> register) {
        List<CommandMeta> metas = new ArrayList<>();
        for (Set<String> words : List.of(login, register)) {
            List<String> all = List.copyOf(words);
            if (!all.isEmpty()) {
                metas.add(proxy.getCommandManager()
                        .metaBuilder(all.getFirst())
                        .aliases(all.subList(1, all.size()).toArray(String[]::new))
                        .plugin(plugin)
                        .build());
            }
        }
        return metas;
    }

    @Override
    public void close() {
        HashingLane open = lane;
        if (open != null) {
            open.close();
            lane = null;
        }
    }
}
