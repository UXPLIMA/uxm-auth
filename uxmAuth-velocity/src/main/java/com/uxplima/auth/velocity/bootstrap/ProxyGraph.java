package com.uxplima.auth.velocity.bootstrap;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.NetworkBulletins;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.PremiumStartLine;
import com.uxplima.auth.adapter.outbound.PwnedPasswords;
import com.uxplima.auth.adapter.outbound.SecretSeal;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlAddressTallies;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.adapter.outbound.SqlLastServers;
import com.uxplima.auth.adapter.outbound.SqlLoginHistory;
import com.uxplima.auth.adapter.outbound.SqlRememberedSessions;
import com.uxplima.auth.adapter.outbound.WebsiteAccounts;
import com.uxplima.auth.adapter.outbound.geo.CountryBook;
import com.uxplima.auth.adapter.outbound.geo.CountryDownload;
import com.uxplima.auth.adapter.outbound.geo.CountryUpkeep;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.api.AuthApiProvider;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.factor.FactorCheck;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.mail.MailCodes;
import com.uxplima.auth.application.mail.MailedLoginCode;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthNotices;
import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.LastServers;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.application.protection.AccountCap;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.application.protection.ConnectionWindow;
import com.uxplima.auth.application.protection.CountryRule;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.application.staff.Alerts;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.adapter.inbound.ClaimWriter;
import com.uxplima.auth.velocity.adapter.inbound.DoorListener;
import com.uxplima.auth.velocity.adapter.inbound.EndedSessions;
import com.uxplima.auth.velocity.adapter.inbound.HoldListener;
import com.uxplima.auth.velocity.adapter.inbound.ProxyAuthApi;
import com.uxplima.auth.velocity.adapter.inbound.ProxyDoor;
import com.uxplima.auth.velocity.adapter.inbound.ProxySessions;
import com.uxplima.auth.velocity.adapter.inbound.RemovedAccounts;
import com.uxplima.auth.velocity.adapter.inbound.ReportListener;
import com.uxplima.auth.velocity.adapter.inbound.RouteListener;
import com.uxplima.auth.velocity.adapter.outbound.ProxyCommands;
import com.uxplima.auth.velocity.adapter.outbound.ProxySecret;
import com.uxplima.auth.velocity.adapter.outbound.event.VelocityEvents;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.util.GameProfile;
import org.jspecify.annotations.Nullable;
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
    /** Who a player waits as, when LimboAPI asks before the proxy's login events; set once the door listens. */
    private Function<Player, Optional<GameProfile>> settleEarly = player -> Optional.empty();

    private NetworkEvents events = new LocalEvents();
    private @Nullable CountryBook countries;
    private @Nullable ScheduledTask countryTask;
    private @Nullable ProxyMail mail;
    /** The API's Velocity events, fired for what happens at this proxy. */
    private final VelocityEvents fired;
    /** Takes the API back from other plugins; null while it is not offered. */
    private @Nullable Runnable withdraw;

    ProxyGraph(Object plugin, ProxyServer proxy, Logger logger, Path folder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
        this.waiting = new WaitingWorld(plugin, proxy, logger, folder);
        this.fired = new VelocityEvents(proxy::getPlayer, new VelocityEvents.Bus() {
            @Override
            public CompletableFuture<?> ask(Object event) {
                return proxy.getEventManager().fire(event);
            }

            @Override
            public void tell(Object event) {
                proxy.getEventManager().fireAndForget(event);
            }
        });
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
        AccountRepository accounts = WebsiteAccounts.beside(
                new SqlAccountRepository(database), database, settings.current().websiteTable(), clock);
        ProxyDoor door = door(settings, accounts, new SqlDisputes(database), clock);
        NetworkSecret secret = secret(settings);
        // Sealed with the secret every member shares, so a code set up on any server reads here.
        SqlFactors factors = new SqlFactors(database, SecretSeal.fromPassphrase(secret.encoded()), clock);
        SqlRememberedSessions remembered = new SqlRememberedSessions(database, clock);
        Remember remember = new Remember(
                remembered,
                () -> settings.current().protection().sessions(),
                () -> settings.current().protection().sharedAddresses(),
                clock::instant);
        AddressBans bans = new AddressBans(
                new SqlAddressTallies(database),
                () -> settings.current().protection().bans(),
                () -> settings.current().protection().sharedAddresses(),
                clock::instant);
        // The proxy tells its own wrong passwords; the servers behind it deliver every alert to their staff.
        LogIn.Failures failures = new Alerts(
                        () -> settings.current().staff().alerts(),
                        new SqlLoginHistory(database),
                        current(),
                        task -> proxy.getScheduler().buildTask(plugin, task).schedule(),
                        clock::instant,
                        AuthNotices.onBan(fired::banned))
                .watching(bans);
        ProxyMail mail = new ProxyMail(proxy, logger, folder, settings, database, failures, words);
        this.mail = mail;
        SecondStep secondStep = new SecondStep(
                factors, mail.emails, () -> settings.current().secondFactor().step(), mail::loginMailed);
        Admission admission = admission(settings, bans, watchCountries(settings, clock), clock);
        ProxySessions sessions = listen(
                door,
                admission,
                settings,
                words,
                clock,
                new SqlLastServers(database, clock),
                remember,
                accounts,
                new Steps(secret, secondStep));
        this.withdraw = AuthApiProvider.offer(new ProxyAuthApi(sessions));
        waiting.open(
                settings,
                accounts,
                words,
                sessions,
                settleEarly,
                guards(settings, accounts, remember, failures, clock, remembered, Codes.of(factors, secondStep, mail)));
        logger.info(PremiumStartLine.of(settings.current().ownership().rule(), "the proxy"));
    }

    /** The bulletins as they are when one is published: the network's, once the proxy has opened it. */
    private NetworkEvents current() {
        return new NetworkEvents() {
            @Override
            public void publish(Bulletin bulletin) {
                events.publish(bulletin);
            }

            @Override
            public void listen(Consumer<Bulletin> listener) {
                events.listen(listener);
            }
        };
    }

    /** What guards the waiting world's own logins and registrations. */
    private WaitingWorld.Guards guards(
            PluginSettings settings,
            AccountRepository accounts,
            Remember remember,
            LogIn.Failures failures,
            Clock clock,
            SqlRememberedSessions remembered,
            Codes codes) {
        AccountCap cap = cap(settings, accounts, clock);
        PwnedPasswords breaches = new PwnedPasswords(
                web,
                PwnedPasswords.SERVICE,
                () -> settings.current().passwordLists().breachCheck(),
                logger::warn);
        return new WaitingWorld.Guards(
                remember,
                failures,
                cap::full,
                breaches,
                events,
                remembered,
                codes.factors(),
                codes.step(),
                codes.mail(),
                codes.emails(),
                codes.mailCodes(),
                fired);
    }

    /** How many accounts one address may make, and which addresses many people share. */
    private static AccountCap cap(PluginSettings settings, AccountRepository accounts, Clock clock) {
        return new AccountCap(
                accounts,
                () -> settings.current().protection().accounts(),
                () -> settings.current().protection().sharedAddresses(),
                clock::instant);
    }

    /** The door's rules, in order: the country, a banned address, then too many connections from one. */
    private Admission admission(PluginSettings settings, AddressBans bans, CountryBook book, Clock clock) {
        return new Admission(
                () -> settings.current().protection().sharedAddresses(),
                List.of(
                        new CountryRule(
                                book,
                                () -> settings.current()
                                        .protection()
                                        .countries()
                                        .rule(),
                                logger::warn),
                        bans.rule(),
                        new ConnectionWindow(
                                () -> settings.current().protection().connections(), clock::instant)));
    }

    /** The country file, opened and fetched off the proxy's threads, at once and then every few hours. */
    private CountryBook watchCountries(PluginSettings settings, Clock clock) {
        CountryBook book = new CountryBook(folder);
        CountryUpkeep upkeep = new CountryUpkeep(
                new CountryDownload(
                        web,
                        CountryDownload.MAXMIND,
                        folder,
                        () -> settings.current().protection().countries().licence(),
                        () -> settings.current().protection().countries().refresh(),
                        clock,
                        logger::warn)::refresh,
                book,
                logger::warn);
        this.countries = book;
        this.countryTask = proxy.getScheduler()
                .buildTask(plugin, upkeep)
                .repeat(6, TimeUnit.HOURS)
                .schedule();
        return book;
    }

    private ProxyDoor door(PluginSettings settings, AccountRepository accounts, SqlDisputes disputes, Clock clock) {
        UuidScheme scheme = settings.current().ownership().uuidScheme();
        return new ProxyDoor(
                atTheDoor(settings, accounts, disputes, clock),
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
    }

    /**
     * The door before every connection, the hold until login, the claim before every server, and the servers'
     * reports after.
     */
    private ProxySessions listen(
            ProxyDoor door,
            Admission admission,
            PluginSettings settings,
            Messages words,
            Clock clock,
            LastServers lastServers,
            Remember remember,
            AccountRepository accounts,
            Steps steps) {
        ProxyCommands commands = proxyCommands(settings);
        ProxySessions sessions = new ProxySessions((id, method) -> {
            fired.loggedIn(id, method);
            commands.loggedIn(id, method);
        });
        Supplier<Optional<Claims>> claims = claims(settings, steps.secret());
        RouteListener routes = routes(sessions, settings, lastServers);
        DoorListener doorListener = new DoorListener(
                door,
                admission,
                remember,
                accounts::findById,
                sessions,
                new DoorWords(words),
                logger,
                steps.secondStep());
        this.settleEarly = doorListener::settleEarly;
        register(doorListener);
        register(new HoldListener(
                sessions,
                proxy.getCommandManager(),
                () -> settings.current().network().allowedCommands(),
                routes::waitingRoom,
                () -> settings.current().network().waitIn() == AuthSettings.WaitIn.LIMBO,
                words));
        register(routes);
        register(new ClaimWriter(sessions, claims, clock));
        proxy.getChannelRegistrar().register(ReportListener.CHANNEL);
        register(new ReportListener(sessions, claims, clock, logger, routes::loggedIn));
        NetworkEvents bulletins = NetworkBulletins.open(
                settings.current().network().redis(), new Bulletins(claims.get().orElseThrow()), logger::warn);
        bulletins.listen(new EndedSessions(sessions));
        bulletins.listen(new RemovedAccounts(proxy::getPlayer, sessions, words));
        this.events = bulletins;
        return sessions;
    }

    private RouteListener routes(ProxySessions sessions, PluginSettings settings, LastServers lastServers) {
        return new RouteListener(
                sessions,
                () -> settings.current().network().routing(),
                lastServers,
                proxy::getServer,
                this::playersOnline,
                RandomGenerator.getDefault());
    }

    @Override
    public void close() {
        if (countryTask != null) {
            countryTask.cancel();
        }
        if (countries != null) {
            try {
                countries.close();
            } catch (IOException unclosable) {
                logger.warn("Could not close the country file: {}", unclosable.getMessage());
            }
        }
        if (withdraw != null) {
            withdraw.run();
            withdraw = null;
        }
        events.close();
        waiting.close();
        if (mail != null) {
            mail.close();
        }
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
     * The network secret: the one in config.conf, or the one this proxy made on its first start, which the console
     * points the operator to. It signs the claims every server behind this proxy reads, and seals the app secrets.
     */
    private NetworkSecret secret(PluginSettings settings) {
        ProxySecret.Loaded loaded =
                ProxySecret.loadOrCreate(folder, settings.current().network().secret());
        if (loaded.created()) {
            logger.info(
                    "This proxy made the network secret and wrote it to {}. Copy it into network.secret in"
                            + " config.conf on every server behind this proxy, and set network.enabled to true there.",
                    loaded.file());
        }
        return loaded.secret();
    }

    /** The signer of the claims every server behind this proxy reads. */
    private static Supplier<Optional<Claims>> claims(PluginSettings settings, NetworkSecret secret) {
        return () -> Optional.of(new Claims(secret, settings.current().network().claimFor()));
    }

    /** The network secret and the step after the password, which the door and the claims read. */
    private record Steps(NetworkSecret secret, SecondStep secondStep) {}

    /** The factor table and the step after the password, which the waiting world reads. */
    private record Codes(
            SqlFactors factors, SecondStep step, FactorCheck.MailedCode mail, Emails emails, MailCodes mailCodes) {

        /** The factor table and the step, with the mail's own table and codes. */
        static Codes of(SqlFactors factors, SecondStep step, ProxyMail mail) {
            return new Codes(factors, step, new MailedLoginCode(mail.emails, mail.codes), mail.emails, mail.codes);
        }
    }

    /** The decision at the pre-login: the web sources, the operator's rule, and the memories of claims and failures. */
    private AtTheDoor atTheDoor(
            PluginSettings settings, AccountRepository accounts, SqlDisputes disputes, Clock clock) {
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

    /** The command lines of the login moments, run as the proxy's own commands. */
    private ProxyCommands proxyCommands(PluginSettings settings) {
        return new ProxyCommands(() -> settings.current().effects(), proxy::getPlayer, new ProxyCommands.Runner() {
            @Override
            public void console(String command) {
                CompletableFuture<Boolean> unused =
                        proxy.getCommandManager().executeAsync(proxy.getConsoleCommandSource(), command);
            }

            @Override
            public void player(Player player, String command) {
                CompletableFuture<Boolean> unused = proxy.getCommandManager().executeAsync(player, command);
            }
        });
    }
}
