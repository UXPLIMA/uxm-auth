package com.uxplima.auth.velocity.bootstrap;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.CommandFile;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.ConfirmFactor;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.application.protection.Captchas;
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
            Guards guards) {
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
            hold(limboApi.get(), settings, accounts, words, network, guards);
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
            Guards guards) {
        Path commands = folder.resolve(COMMANDS);
        Set<String> login = CommandFile.labels(commands, "login");
        Set<String> register = CommandFile.labels(commands, "register");
        Set<String> change = CommandFile.labels(commands, "changepassword");
        Set<String> captcha = CommandFile.labels(commands, "captcha");
        Set<String> factor = CommandFile.labels(commands, "2fa");
        LimboLogin.Words labels =
                new LimboLogin.Words(() -> login, () -> register, () -> change, () -> captcha, () -> factor);
        LimboLogin passwords = passwords(settings, accounts, words, labels, guards);
        LimboWait wait = new LimboWait(
                LimboWorld.open(
                        limboApi,
                        folder,
                        settings.current().network(),
                        metas(List.of(login, register, change, captcha, factor)),
                        logger),
                network::waitsAtTheProxy,
                (player, exit) -> visit(passwords, network, guards.remember(), player, exit),
                () -> settings.current().timeouts(),
                player -> words.render(player, Keys.KICK_TIMEOUT),
                logger);
        proxy.getEventManager().register(plugin, wait);
        logger.info("Players who must log in wait in the proxy's own world, built with LimboAPI.");
    }

    /**
     * What guards the waiting world's door: the remembered sessions, where a wrong password counts, the limit on
     * the accounts one address makes, the breach check, and where a changed password ends the account's other
     * sessions.
     */
    record Guards(
            Remember remember,
            LogIn.Failures failures,
            RegisterAccount.Cap cap,
            BreachedPasswords breaches,
            NetworkEvents events,
            RememberedSessions remembered,
            Factors factors,
            SecondStep secondStep,
            MailCodes mailCodes) {}

    private LimboLogin passwords(
            PluginSettings settings,
            SqlAccountRepository accounts,
            Messages words,
            LimboLogin.Words labels,
            Guards guards) {
        PluginSettings.Snapshot boot = settings.current();
        AuthSettings.Lane sizes = boot.lane();
        HashingLane hashingLane = new HashingLane(sizes.threads(), sizes.maxThreads(), sizes.queue());
        this.lane = hashingLane;
        Hashing hashing = boot.hashers();
        Sessions sessions = new Sessions();
        Clock clock = Clock.systemUTC();
        Captchas captchas =
                new Captchas(() -> settings.current().protection().captcha(), new SecureRandom(), clock::instant);
        return new LimboLogin(
                new ReadTheName(
                        accounts,
                        () -> settings.current().namePolicy(),
                        () -> settings.current().passwords().maxAttempts()),
                new LogIn(
                        accounts,
                        hashing,
                        hashingLane,
                        sessions,
                        clock,
                        guards.failures(),
                        () -> settings.current().passwordPolicy(),
                        captchas,
                        guards.secondStep()),
                new RegisterAccount(
                        accounts,
                        hashing,
                        hashingLane,
                        sessions,
                        () -> settings.current().passwordPolicy(),
                        clock,
                        guards.cap(),
                        guards.breaches(),
                        captchas::blocksRegistration),
                change(settings, accounts, new Hashers(hashing, hashingLane), sessions, guards),
                captchas,
                sessions,
                words,
                labels,
                () -> settings.current().passwords().confirmOnRegister(),
                guards.secondStep(),
                codes(settings, accounts, sessions, guards, new Hashers(hashing, hashingLane)));
    }

    /** The code of the second step, checked on the hashing lane: the factor table is read, backup codes hashed. */
    private static LimboLogin.CodeCheck codes(
            PluginSettings settings, SqlAccountRepository accounts, Sessions sessions, Guards guards, Hashers hashers) {
        FactorCheck check = new FactorCheck(
                guards.factors(),
                hashers.hashing(),
                () -> settings.current().secondFactor().setup().totp(),
                () -> settings.current().secondFactor().setup().backup(),
                (account, typed) -> guards.mailCodes().takes(account, Emails.Purpose.LOGIN, typed));
        ConfirmFactor confirm = new ConfirmFactor(accounts, sessions, check, guards.failures(), Instant::now);
        return (id, code, address) -> hashers.lane().submit(() -> confirm.confirm(id, code, address));
    }

    /** The hashers of the waiting world and the lane they run on. */
    private record Hashers(Hashing hashing, HashingLane lane) {}

    private static ChangePassword change(
            PluginSettings settings, SqlAccountRepository accounts, Hashers hashers, Sessions sessions, Guards guards) {
        return new ChangePassword(
                accounts,
                hashers.hashing(),
                hashers.lane(),
                sessions,
                () -> settings.current().passwordPolicy(),
                guards.events(),
                guards.remembered(),
                guards.breaches());
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
    private List<CommandMeta> metas(List<Set<String>> labels) {
        List<CommandMeta> metas = new ArrayList<>();
        for (Set<String> words : labels) {
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
