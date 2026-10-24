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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.CommandFile;
import com.uxplima.auth.adapter.outbound.PluginSettings;
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
import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.velocity.adapter.inbound.LimboLogin;
import com.uxplima.auth.velocity.adapter.inbound.LimboRecovery;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisit;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisits;
import com.uxplima.auth.velocity.adapter.inbound.ProxySessions;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboKit;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboKits;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboWait;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.util.GameProfile;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The proxy's waiting world, when the operator asks for it and LimboAPI is there to build it. LimboAPI's classes
 * are reached only past the check that the plugin is loaded, so a proxy without it never links them. A proxy asked
 * for the world that cannot build it does not start, so it never sends a waiting player on to a server.
 */
final class WaitingWorld implements AutoCloseable {

    private static final String LIMBO_API = "limboapi";
    private static final String COMMANDS = "commands.conf";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private @Nullable HashingLane lane;
    private @Nullable ForceLogin force;

    WaitingWorld(Object plugin, ProxyServer proxy, Logger logger, Path folder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /**
     * Hold every player who waits for the proxy's password in the world, when the settings ask for it.
     *
     * @param door who a player waits as, settled by the door when LimboAPI asks, before the proxy's login events
     * @throws NoWaitingWorld when the settings ask for the world and the proxy cannot build it
     */
    void open(
            PluginSettings settings,
            AccountRepository accounts,
            Messages words,
            ProxySessions network,
            Function<Player, Optional<GameProfile>> door,
            Guards guards) {
        if (settings.current().network().waitIn() != AuthSettings.WaitIn.LIMBO) {
            return;
        }
        Optional<?> limboApi = proxy.getPluginManager().getPlugin(LIMBO_API).flatMap(PluginContainer::getInstance);
        if (limboApi.isEmpty()) {
            throw new NoWaitingWorld(
                    "network.wait-in is limbo, but LimboAPI is not on this proxy. Install LimboAPI, or set"
                            + " network.wait-in = server and put uxmAuth on every server behind the proxy.",
                    null);
        }
        Optional<LimboKit> kit = LimboKits.of(limboApi.get());
        if (kit.isEmpty()) {
            throw new NoWaitingWorld(
                    "The LimboAPI on this proxy is a release uxmAuth does not know. uxmAuth speaks to LimboAPI 1.1"
                            + " and LimboAPI 2.",
                    null);
        }
        try {
            hold(kit.get(), limboApi.get(), settings, accounts, words, network, door, guards);
        } catch (RuntimeException | LinkageError foreign) {
            close();
            throw new NoWaitingWorld("LimboAPI could not build the waiting world: " + foreign, foreign);
        }
    }

    private void hold(
            LimboKit kit,
            Object limboApi,
            PluginSettings settings,
            AccountRepository accounts,
            Messages words,
            ProxySessions network,
            Function<Player, Optional<GameProfile>> door,
            Guards guards) {
        LimboLogin.Words labels = labels(folder.resolve(COMMANDS));
        LimboLogin passwords = passwords(settings, accounts, words, labels, guards);
        LimboVisits reach = new LimboVisits((id, address) -> forced(id, address));
        guards.bulletins().listen(reach);
        LimboWait wait = new LimboWait(
                kit.open(limboApi, folder, settings.current().network(), metas(labels), logger),
                player -> door.apply(player)
                        .map(profile -> new LimboWait.Waiter(profile.getId(), new PlayerName(profile.getName()))),
                (player, waiter, exit) -> visit(passwords, network, guards.remember(), player, waiter, exit, reach),
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
            NetworkEvents bulletins,
            RememberedSessions remembered,
            Factors factors,
            SecondStep secondStep,
            FactorCheck.MailedCode mailedCode,
            Emails emails,
            MailCodes mailCodes,
            AuthEvents events) {}

    private LimboLogin passwords(
            PluginSettings settings,
            AccountRepository accounts,
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
        this.force = new ForceLogin(accounts, sessions, guards.bulletins(), clock);
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
                        guards.secondStep(),
                        guards.events()),
                new RegisterAccount(
                        accounts,
                        hashing,
                        hashingLane,
                        sessions,
                        () -> settings.current().passwordPolicy(),
                        clock,
                        guards.cap(),
                        guards.breaches(),
                        captchas::blocksRegistration,
                        guards.events()),
                change(settings, accounts, new Hashers(hashing, hashingLane), sessions, guards),
                captchas,
                sessions,
                words,
                labels,
                () -> settings.current().passwords().confirmOnRegister(),
                guards.secondStep(),
                codes(settings, accounts, sessions, guards, new Hashers(hashing, hashingLane)),
                recovery(settings, accounts, sessions, words, guards, new Hashers(hashing, hashingLane)));
    }

    /** A forgotten password, by a code mailed to the linked address; the new one is hashed on the lane. */
    private static LimboRecovery recovery(
            PluginSettings settings,
            AccountRepository accounts,
            Sessions sessions,
            Messages words,
            Guards guards,
            Hashers hashers) {
        RecoverPassword recover = new RecoverPassword(
                sessions,
                guards.emails(),
                guards.mailCodes(),
                accounts,
                hashers.hashing(),
                hashers.lane(),
                () -> settings.current().passwordPolicy(),
                guards.breaches(),
                guards.remembered(),
                guards.bulletins());
        return new LimboRecovery(recover, sessions, words);
    }

    /** The code of the second step, checked on the hashing lane: the factor table is read, backup codes hashed. */
    private static LimboLogin.CodeCheck codes(
            PluginSettings settings, AccountRepository accounts, Sessions sessions, Guards guards, Hashers hashers) {
        FactorCheck check = new FactorCheck(
                guards.factors(),
                hashers.hashing(),
                () -> settings.current().secondFactor().setup().totp(),
                () -> settings.current().secondFactor().setup().backup(),
                guards.mailedCode());
        ConfirmFactor confirm = new ConfirmFactor(accounts, sessions, check, guards.failures(), Instant::now);
        return (id, code, address) -> hashers.lane().submit(() -> confirm.confirm(id, code, address));
    }

    /** The hashers of the waiting world and the lane they run on. */
    private record Hashers(Hashing hashing, HashingLane lane) {}

    private static ChangePassword change(
            PluginSettings settings, AccountRepository accounts, Hashers hashers, Sessions sessions, Guards guards) {
        return new ChangePassword(
                accounts,
                hashers.hashing(),
                hashers.lane(),
                sessions,
                () -> settings.current().passwordPolicy(),
                guards.bulletins(),
                guards.remembered(),
                guards.breaches());
    }

    /** Lets a connection waiting in the world in as forced, on the lane: the account table is written. */
    private CompletableFuture<Boolean> forced(UUID id, String address) {
        HashingLane open = lane;
        ForceLogin force = this.force;
        if (open == null || force == null) {
            return CompletableFuture.completedFuture(false);
        }
        return open.submit(() -> force.admit(id, address).isPresent());
    }

    private static LimboVisit visit(
            LimboLogin passwords,
            ProxySessions network,
            Remember remember,
            Player player,
            LimboWait.Waiter waiter,
            LimboVisit.Exit exit,
            LimboVisit.Reach reach) {
        return new LimboVisit(
                passwords,
                network,
                remember,
                waiter.id(),
                waiter.name(),
                player.getRemoteAddress().getAddress().getHostAddress(),
                player.getVirtualHost().map(InetSocketAddress::getHostString).orElse(""),
                player,
                exit,
                reach);
    }

    /** Each command the waiting world answers, in every word commands.conf gives it. */
    private static LimboLogin.Words labels(Path commands) {
        Set<String> login = CommandFile.labels(commands, "login");
        Set<String> register = CommandFile.labels(commands, "register");
        Set<String> change = CommandFile.labels(commands, "changepassword");
        Set<String> captcha = CommandFile.labels(commands, "captcha");
        Set<String> factor = CommandFile.labels(commands, "2fa");
        Set<String> recover = CommandFile.labels(commands, "recover");
        return new LimboLogin.Words(
                () -> login, () -> register, () -> change, () -> captcha, () -> factor, () -> recover);
    }

    /** The waiting world's commands as the client must know them to send them, in every word commands.conf gives. */
    private List<CommandMeta> metas(LimboLogin.Words labels) {
        List<CommandMeta> metas = new ArrayList<>();
        for (Supplier<Set<String>> each : List.of(
                labels.loginLabels(),
                labels.registerLabels(),
                labels.changeLabels(),
                labels.captchaLabels(),
                labels.factorLabels(),
                labels.recoverLabels())) {
            List<String> all = List.copyOf(each.get());
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
