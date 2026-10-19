package com.uxplima.auth.paper.bootstrap;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.NetworkBulletins;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.PwnedPasswords;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlAddressTallies;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.SqlEmails;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.adapter.outbound.SqlLoginHistory;
import com.uxplima.auth.adapter.outbound.SqlRememberedSessions;
import com.uxplima.auth.adapter.outbound.TableCopy;
import com.uxplima.auth.adapter.outbound.WebsiteAccounts;
import com.uxplima.auth.adapter.outbound.geo.CountryBook;
import com.uxplima.auth.adapter.outbound.geo.CountryDownload;
import com.uxplima.auth.adapter.outbound.geo.CountryUpkeep;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LetBedrockIn;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.factor.FactorStates;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.migrate.ChangeScheme;
import com.uxplima.auth.application.migrate.MoveDatabase;
import com.uxplima.auth.application.perf.TimedAccounts;
import com.uxplima.auth.application.perf.Timings;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthNotices;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.application.protection.AccountCap;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.application.protection.Admission;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.application.protection.ConnectionWindow;
import com.uxplima.auth.application.protection.CountryRule;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.application.staff.AccountInfo;
import com.uxplima.auth.application.staff.Alerts;
import com.uxplima.auth.application.staff.FactorOff;
import com.uxplima.auth.application.staff.ForceLogin;
import com.uxplima.auth.application.staff.ForceRegister;
import com.uxplima.auth.application.staff.MarkKind;
import com.uxplima.auth.application.staff.ResolveConflict;
import com.uxplima.auth.application.staff.SetPassword;
import com.uxplima.auth.application.staff.Unregister;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.CaptchaCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ChangePasswordCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ClaimCommand;
import com.uxplima.auth.paper.adapter.inbound.command.EmailCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LoginCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LogoutCommand;
import com.uxplima.auth.paper.adapter.inbound.command.PlayerCommandLabels;
import com.uxplima.auth.paper.adapter.inbound.command.PremiumCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RecoverCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RegisterCommand;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.command.SecretBranches;
import com.uxplima.auth.paper.adapter.inbound.command.SessionCommand;
import com.uxplima.auth.paper.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.EndedSessions;
import com.uxplima.auth.paper.adapter.inbound.hold.Greeter;
import com.uxplima.auth.paper.adapter.inbound.hold.HoldListener;
import com.uxplima.auth.paper.adapter.inbound.hold.LoginRecords;
import com.uxplima.auth.paper.adapter.inbound.hold.Notices;
import com.uxplima.auth.paper.adapter.inbound.hold.Remembering;
import com.uxplima.auth.paper.adapter.inbound.hold.WaitingClock;
import com.uxplima.auth.paper.adapter.inbound.hold.Welcomes;
import com.uxplima.auth.paper.adapter.inbound.listener.Arrivals;
import com.uxplima.auth.paper.adapter.inbound.listener.BedrockAdmission;
import com.uxplima.auth.paper.adapter.inbound.listener.ConnectionListener;
import com.uxplima.auth.paper.adapter.inbound.listener.HelloAtTheDoor;
import com.uxplima.auth.paper.adapter.inbound.listener.NameHolders;
import com.uxplima.auth.paper.adapter.inbound.listener.NetworkDoor;
import com.uxplima.auth.paper.adapter.inbound.mail.AddressDuty;
import com.uxplima.auth.paper.adapter.inbound.screen.AuthScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.BedrockForms;
import com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen;
import com.uxplima.auth.paper.adapter.inbound.screen.ConfigurationGate;
import com.uxplima.auth.paper.adapter.inbound.screen.FormAnswers;
import com.uxplima.auth.paper.adapter.inbound.screen.Guest;
import com.uxplima.auth.paper.adapter.inbound.screen.InWorldScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.PaperScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.PermissionDefaults;
import com.uxplima.auth.paper.adapter.inbound.screen.PlayerGuest;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenAnswers;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenClickListener;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenRecovery;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenRouter;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenShower;
import com.uxplima.auth.paper.adapter.inbound.staff.AlertDelivery;
import com.uxplima.auth.paper.adapter.inbound.staff.DatabaseDesk;
import com.uxplima.auth.paper.adapter.inbound.staff.ForcedLogins;
import com.uxplima.auth.paper.adapter.inbound.staff.RemovedAccounts;
import com.uxplima.auth.paper.adapter.inbound.staff.StaffDesk;
import com.uxplima.auth.paper.adapter.inbound.staff.StaffView;
import com.uxplima.auth.paper.adapter.inbound.staff.WaitingAccounts;
import com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink;
import com.uxplima.auth.paper.adapter.outbound.captcha.CaptchaMap;
import com.uxplima.auth.paper.adapter.outbound.client.DialogClients;
import com.uxplima.auth.paper.adapter.outbound.discord.DiscordNotices;
import com.uxplima.auth.paper.adapter.outbound.effect.AuthMoments;
import com.uxplima.auth.paper.adapter.outbound.event.PaperEvents;
import com.uxplima.auth.paper.adapter.outbound.handshake.PremiumHelloDoor;
import com.uxplima.auth.paper.adapter.outbound.handshake.ServerHellos;
import com.uxplima.auth.paper.adapter.outbound.hold.HeldStateStore;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.auth.paper.adapter.outbound.hold.Passes;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilter;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilters;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordMask;
import com.uxplima.auth.paper.adapter.outbound.network.ChannelReports;
import com.uxplima.auth.paper.adapter.outbound.world.LoginWorld;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import org.jspecify.annotations.Nullable;

/**
 * The way in, built once at enable: the sessions, the lane, the use cases, the hold, the door, the commands
 * and the log filter.
 *
 * <p>The hashing numbers and the lane sizes are read here and nowhere else, so a change to them applies at the
 * next start. Everything else is read from the settings on every use, so a reload reaches it.
 */
final class AuthGraph implements AutoCloseable {

    final Sessions sessions = new Sessions();
    /** The time spent hashing, querying the accounts table and asking Mojang, for /uxmauth perf. */
    final Timings timings = new Timings(System::nanoTime);
    /** What is known of each account's second factor while its player is here, for the placeholders. */
    final FactorStates factorStates;

    final HashingLane lane;
    final ConnectionListener connections;
    final HoldListener hold;
    final LoginCommand login;
    final RegisterCommand register;
    final ChangePasswordCommand changePassword;
    final LogoutCommand logout;
    final ClaimCommand claim;
    final PremiumCommand premium;
    final SessionCommand session;
    final FactorGraph factors;
    final TwoFactorCommand twoFactor;
    final EmailCommand email;
    private final AddressDuty addressDuty;
    final SqlLoginHistory history;
    final StaffView staff;
    final StaffGraph staffing;
    final StaffDesk desk;
    final DatabaseDesk databases;
    final RecoverCommand recover;
    private final MailGraph mail;
    final CaptchaCommand captchaCommand;
    final ScreenClickListener screenClicks;
    final ConfigurationGate gate;
    private final LoginWorld loginWorld;
    private final BedrockLink bedrock;

    private final Plugin plugin;
    private final PasswordLogFilters filters;
    /** How often the country file is checked; it is fetched only once it is as old as the settings allow. */
    private static final Duration COUNTRY_CHECK = Duration.ofHours(6);

    private final HttpClient web = HttpClient.newHttpClient();
    private final CountryBook countries;
    private final CountryUpkeep countryUpkeep;
    private final Scheduler scheduler;
    private @Nullable TaskHandle countryTask;
    private final TaskHandle historyTask;
    private final NetworkEvents events;
    private final Alerts alerts;
    private final DiscordNotices discord;
    /** How many Discord messages may wait; past it the oldest goes. */
    private static final int DISCORD_QUEUE = 100;

    private final ChannelReports channelReports;
    private final ServerHellos hellos;
    /** Who holds a name, by the operator's sources: the door asks it, and so do staff marking an account premium. */
    private final PremiumLookup lookup;

    AuthGraph(
            Plugin plugin,
            Scheduler scheduler,
            PluginSettings settings,
            ScreenFile screenFile,
            Database database,
            Messages messages,
            Styler styler,
            ConfiguredCommands commands) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Path folder = plugin.getDataFolder().toPath();
        this.countries = new CountryBook(folder);
        this.countryUpkeep = countryUpkeep(plugin, settings, countries, folder);
        PluginSettings.Snapshot boot = settings.current();
        AuthSettings.Lane sizes = boot.lane();
        this.lane = new HashingLane(sizes.threads(), sizes.maxThreads(), sizes.queue());
        this.channelReports = new ChannelReports(plugin, () -> claims(settings), Clock.systemUTC());
        Hashing hashing = boot.hashers().observed(timings::start, spent -> timings.record(Timings.Kind.HASHING, spent));
        AccountRepository accounts = new TimedAccounts(
                WebsiteAccounts.beside(
                        new SqlAccountRepository(database), database, boot.websiteTable(), Clock.systemUTC()),
                timings);
        SqlRememberedSessions remembered = new SqlRememberedSessions(database);
        this.history = new SqlLoginHistory(database);
        Remember remember = new Remember(
                remembered,
                () -> settings.current().protection().sessions(),
                () -> settings.current().protection().sharedAddresses(),
                Instant::now);
        AddressBans bans = new AddressBans(
                new SqlAddressTallies(database),
                () -> settings.current().protection().bans(),
                () -> settings.current().protection().sharedAddresses(),
                Instant::now);
        this.events = bulletins(settings, plugin.getLogger());
        PaperEvents fired = new PaperEvents(
                plugin.getServer()::getPlayer, plugin.getServer().getPluginManager()::callEvent);
        this.discord = new DiscordNotices(
                () -> settings.current().discord(), DiscordNotices.WEBHOOK, plugin.getLogger()::warning, DISCORD_QUEUE);
        this.alerts = new Alerts(
                () -> settings.current().staff().alerts(),
                history,
                events,
                task -> scheduler.async(task),
                Instant::now,
                AuthNotices.both(discord, AuthNotices.onBan(fired::banned)));
        LogIn.Failures failures = alerts.watching(bans);
        Clock clock = Clock.systemUTC();
        Set<String> labels = PlayerCommandLabels.of(commands);

        Passes passes = new Passes();
        this.loginWorld = new LoginWorld(plugin.getServer(), screenFile::current, plugin.getLogger());
        this.hold = new HoldListener(
                sessions,
                () -> settings.current().hold(),
                () -> labels,
                plugin.getName().toLowerCase(Locale.ROOT),
                passes::isPassing);
        HoldKeeper keeper = new HoldKeeper(
                plugin,
                scheduler,
                new HeldStateStore(plugin.getDataFolder().toPath().resolve("held")),
                () -> settings.current().hold(),
                hold::isHeld,
                loginWorld::room,
                passes);
        AuthMoments moments = new AuthMoments(
                plugin.getServer(),
                scheduler,
                messages,
                styler,
                () -> settings.current().effects());
        WaitingClock waiting =
                new WaitingClock(scheduler, sessions, () -> settings.current().timeouts(), clock, moments, messages);
        // A second is enough for a client to report its language; an English one may never report it.
        Greeter greeter = new Greeter(
                moments, (player, task) -> scheduler.entityLater(player, Duration.ofSeconds(1), task), hold::isHeld);
        SqlDisputes disputes = new SqlDisputes(database);
        this.lookup = new PremiumLookup(
                new PremiumSources(web, () -> settings.current().premium(), clock)::ordered,
                () -> settings.current().premium().keepAnswers(),
                clock,
                timings);
        AtTheDoor atTheDoor = atTheDoor(settings, accounts, disputes, clock);
        this.hellos = ServerHellos.startOn(new PremiumHelloDoor(atTheDoor, scheduler), plugin.getLogger());
        ClaimName claims = new ClaimName(
                sessions, atTheDoor, () -> settings.current().ownership().rule());
        AuthScreens screens = new AuthScreens(
                screenFile::current,
                () -> settings.current().passwords(),
                messages,
                styler,
                () -> hellos.installed() && claims.takesClaims(),
                () -> settings.current().protection().sessions().mode() == Remember.Mode.CHOSEN,
                () -> settings.current().mail().enabled());
        this.bedrock = BedrockLink.on(plugin.getServer(), plugin.getLogger());
        // A form's answer goes where a dialog's does; the listener that takes them is built further down.
        AtomicReference<FormAnswers> formAnswers = new AtomicReference<>((player, action, answer) -> {});
        ScreenShower shower = new ScreenRouter(
                new PaperScreens(),
                new BedrockForms(
                        BedrockScreen.forServer(plugin.getServer()),
                        (player, action, answer) -> Objects.requireNonNull(formAnswers.get(), "formAnswers")
                                .answer(player, action, answer)),
                id -> bedrock.player(id).isPresent());
        Welcomes welcomes = new Welcomes();
        Notices notices = new Notices(messages);
        Replies replies = new Replies(scheduler, messages, plugin.getLogger());
        Captchas captchas =
                new Captchas(() -> settings.current().protection().captcha(), new SecureRandom(), Instant::now);
        CaptchaScreen captcha = new CaptchaScreen(captchas, screens, shower, messages, captchaHand(plugin, settings));
        AuthSettings.Network network = settings.current().network();
        SqlFactors factorTable = new SqlFactors(
                database,
                FactorGraph.seal(
                        network.enabled(),
                        network.secret(),
                        plugin.getDataFolder().toPath()),
                Clock.systemUTC());
        SqlEmails emailTable = new SqlEmails(database, Clock.systemUTC());
        this.factorStates = new FactorStates(factorTable, emailTable);
        this.mail = new MailGraph(
                plugin, settings, database, failures, hashing, lane, messages, factorStates.watching(emailTable));
        this.factors = new FactorGraph(
                plugin,
                settings,
                database,
                accounts,
                sessions,
                failures,
                id -> bedrock.player(id).isPresent(),
                hashing,
                lane,
                replies,
                moments,
                messages,
                screens,
                shower,
                mail,
                factorStates.watching(factorTable));
        LogIn logIn = new LogIn(
                accounts,
                hashing,
                lane,
                sessions,
                clock,
                failures,
                () -> settings.current().passwordPolicy(),
                captchas,
                factors.secondStep,
                fired);
        AccountCap cap = new AccountCap(
                accounts,
                () -> settings.current().protection().accounts(),
                () -> settings.current().protection().sharedAddresses(),
                Instant::now);
        PwnedPasswords breaches = new PwnedPasswords(
                web,
                PwnedPasswords.SERVICE,
                () -> settings.current().passwordLists().breachCheck(),
                plugin.getLogger()::warning);
        RegisterAccount registering = new RegisterAccount(
                accounts,
                hashing,
                lane,
                sessions,
                () -> settings.current().passwordPolicy(),
                clock,
                cap::full,
                breaches,
                captchas::blocksRegistration,
                fired);
        ChangePassword change = new ChangePassword(
                accounts,
                hashing,
                lane,
                sessions,
                () -> settings.current().passwordPolicy(),
                events,
                remembered,
                breaches,
                fired);
        RecoverPassword recovering = mail.recoverPassword(sessions, accounts, hashing, remembered, events, breaches);
        ScreenAnswers answers = new ScreenAnswers(
                screens,
                registering,
                logIn,
                screenFile::current,
                () -> settings.current().passwords(),
                messages,
                shower,
                id -> sessions.state(id).orElse(null) instanceof AuthState.Unregistered,
                claims::claim,
                sessions::chose,
                change,
                captcha,
                factors.screen,
                new ScreenRecovery(recovering, sessions, screens, shower, messages));
        this.gate = new ConfigurationGate(
                sessions,
                screenFile::current,
                () -> settings.current().timeouts(),
                screens,
                answers,
                shower,
                messages,
                BedrockLink.javaOnly(bedrock, DialogClients.on(plugin.getServer(), plugin.getLogger())),
                id -> bedrock.player(id).isPresent(),
                new PermissionDefaults(plugin.getServer().getPluginManager()),
                welcomes,
                plugin.getLogger());
        Remembering remembering = new Remembering(remember, sessions, scheduler);
        InWorldScreens inWorld = new InWorldScreens(
                screenFile::current,
                screens,
                gate::sentToTheWorld,
                (player, task) -> scheduler.entityLater(player, Duration.ofSeconds(1), task),
                hold::isHeld,
                shower,
                captcha,
                factors.screen);
        this.captchaCommand = new CaptchaCommand(captcha, sessions::state, inWorld, messages);
        MailGraph.Email email = mail.email(sessions, id -> bedrock.player(id).isPresent(), replies, moments);
        this.email = email.command();
        this.addressDuty = email.duty();
        LoginRecords records = new LoginRecords(
                history,
                task -> scheduler.async(task),
                clock,
                plugin.getLogger()::warning,
                alerts::landed,
                factorStates,
                fired::loggedIn);
        // At start, and once a day after, a login older than the window goes.
        this.historyTask = scheduler.asyncTimer(
                Duration.ZERO,
                Duration.ofDays(1),
                task -> records.prune(settings.current().staff().historyKeep()));
        Door door = new Door(
                sessions,
                keeper,
                waiting,
                moments,
                greeter,
                inWorld,
                welcomes,
                notices,
                channelReports,
                remembering,
                records,
                Door.Duty.inTurn(factors.duty, addressDuty));

        this.connections = new ConnectionListener(
                scheduler,
                sessions,
                new Arrivals(
                        new ReadTheName(
                                accounts,
                                () -> settings.current().namePolicy(),
                                () -> settings.current().passwords().maxAttempts()),
                        new BedrockAdmission(
                                bedrock,
                                new LetBedrockIn(
                                        accounts,
                                        () -> settings.current().passwords().maxAttempts(),
                                        clock),
                                () -> settings.current().bedrock().autoLogin()),
                        new HelloAtTheDoor(
                                hellos,
                                atTheDoor,
                                new LetPremiumIn(
                                        accounts,
                                        disputes,
                                        () -> settings.current().ownership().conflict(),
                                        () -> settings.current().ownership().renameMark(),
                                        // The scheme this start recorded: a reload cannot change it.
                                        boot.ownership().uuidScheme(),
                                        clock,
                                        fired),
                                new Moves(accounts)),
                        messages,
                        scheduler,
                        plugin.getServer()::createProfile,
                        () -> settings.current().ownership().crackedMark(),
                        boot.ownership().uuidScheme(),
                        new NetworkDoor(() -> claims(settings), accounts::findById, clock, plugin.getLogger()),
                        admission(settings, bans, countries, plugin.getLogger()),
                        remember::resumes,
                        factors.secondStep),
                notices,
                door,
                keeper,
                new NameHolders(plugin.getServer(), gate::waitingFrom),
                () -> settings.current().names().reconnectFromSameAddress(),
                messages,
                plugin.getLogger());
        ConnectionListener joins = connections;
        this.login =
                new LoginCommand(logIn, door, joins::openingOf, moments, messages, replies, captcha, factors.screen);
        this.register = new RegisterCommand(
                registering, door, joins::openingOf, () -> settings.current().passwords(), messages, replies, captcha);
        this.changePassword = new ChangePasswordCommand(
                change, door, moments, () -> settings.current().passwords(), messages, replies);
        LogOut logOut =
                new LogOut(sessions, () -> settings.current().passwords().maxAttempts(), remembered);
        this.logout = new LogoutCommand(logOut, door, moments, messages);
        events.listen(
                new EndedSessions(plugin.getServer()::getPlayer, sessions, scheduler, logOut, door, moments, messages));
        this.claim = new ClaimCommand(claims, hellos::installed, messages);
        this.premium = new PremiumCommand(claims, hellos::installed, sessions, messages);
        this.session = new SessionCommand(
                remember,
                remembering,
                sessions,
                () -> settings.current().protection().sessions(),
                scheduler,
                messages);
        Function<Player, Guest> guests =
                player -> new PlayerGuest(player, joins.openingOf(player), door, moments, replies);
        this.screenClicks = new ScreenClickListener(answers, guests, scheduler);
        this.twoFactor = factors.command(guests);
        this.staffing = new StaffGraph(
                plugin,
                scheduler,
                messages,
                styler,
                new AccountInfo(
                        accounts,
                        history,
                        factors.factors,
                        mail.emails,
                        () -> settings.current().staff().info()),
                lane,
                replies,
                () -> settings.current().staff().dates().withZone(ZoneId.systemDefault()),
                id -> bedrock.player(id).isPresent());
        this.staff = staffing.view;
        ForceLogin force = new ForceLogin(accounts, sessions, events, clock);
        events.listen(new ForcedLogins(plugin.getServer()::getPlayer, force, lane, replies, door, messages));
        events.listen(new RemovedAccounts(plugin.getServer()::getPlayer, scheduler, messages));
        events.listen(new AlertDelivery(plugin.getServer()::getOnlinePlayers, scheduler, messages));
        this.databases = databaseDesk(plugin, scheduler, settings, database, replies, messages);
        this.desk = staffing.desk(
                new StaffDesk.Cases(
                        force,
                        bans,
                        new Unregister(accounts, events, fired),
                        new SetPassword(
                                accounts,
                                hashing,
                                () -> settings.current().passwordPolicy(),
                                breaches,
                                remembered,
                                events,
                                fired),
                        new ForceRegister(
                                accounts,
                                hashing,
                                () -> settings.current().namePolicy(),
                                () -> settings.current().passwordPolicy(),
                                breaches,
                                clock),
                        new FactorOff(accounts, factors.factors, mail.emails),
                        new MarkKind(accounts, lookup::lookup, boot.ownership().uuidScheme(), events, fired),
                        new ResolveConflict(
                                accounts,
                                disputes,
                                () -> settings.current().ownership().renameMark(),
                                events,
                                clock)),
                new StaffDesk.Reach(
                        name -> Optional.ofNullable(plugin.getServer().getPlayerExact(name))
                                .map(Player::getUniqueId),
                        new WaitingAccounts(
                                plugin.getServer()::getPlayerExact,
                                sessions,
                                () -> settings.current().passwords().maxAttempts(),
                                door,
                                scheduler,
                                messages)),
                action -> settings.current().staff().allowsInGame(action),
                alerts::staffActed);
        this.recover =
                mail.recover(recovering, sessions, id -> bedrock.player(id).isPresent(), replies, moments);
        formAnswers.set(screenClicks);
        PasswordMask.Branches staffSecrets =
                new PasswordMask.Branches(SecretBranches.labels(commands), SecretBranches.words(commands));
        this.filters =
                new PasswordLogFilters(new PasswordLogFilter(new PasswordMask(() -> labels, () -> staffSecrets)));
    }

    /** The map the captcha is drawn on, or none when the server has no world to keep one in. */
    private CaptchaScreen.Hand captchaHand(Plugin plugin, PluginSettings settings) {
        return CaptchaMap.open(
                        plugin.getServer(),
                        plugin.getDataFolder().toPath().resolve("captcha-map.id"),
                        () -> settings.current().protection().captcha(),
                        id -> bedrock.player(id).isPresent(),
                        () -> settings.current().hold().hideInventory(),
                        new SecureRandom(),
                        plugin.getLogger())
                .<CaptchaScreen.Hand>map(map -> map)
                .orElse(CaptchaScreen.Hand.NONE);
    }

    /** Listens, filters the log, and sends every player already online to the door. */
    void start() {
        loginWorld.prepare();
        // The country file is opened and fetched off the server thread, at once and then every few hours.
        this.countryTask = scheduler.asyncTimer(Duration.ZERO, COUNTRY_CHECK, handle -> countryUpkeep.run());
        bedrock.prefix()
                .filter(BedrockLink::collides)
                .ifPresent(prefix -> plugin.getLogger()
                        .warning("Floodgate's username-prefix is \"" + prefix
                                + "\". A Bedrock name with it can be a Java"
                                + " name too, and an unlinked Bedrock player could then reach a Java player's account."
                                + " Set it in Floodgate's config.yml to a character no Java name has, such as a dot."));
        // The door tells the proxy of a network what it did, on this channel; a server alone sends nothing on it.
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, Claims.CHANNEL);
        plugin.getServer().getPluginManager().registerEvents(connections, plugin);
        plugin.getServer().getPluginManager().registerEvents(channelReports, plugin);
        plugin.getServer().getPluginManager().registerEvents(hold, plugin);
        plugin.getServer().getPluginManager().registerEvents(screenClicks, plugin);
        plugin.getServer().getPluginManager().registerEvents(factors.guard, plugin);
        plugin.getServer().getPluginManager().registerEvents(addressDuty, plugin);
        plugin.getServer().getPluginManager().registerEvents(gate, plugin);
        filters.install();
        staffing.start();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            // A player who was online before this enable has no session yet. They are asked again, which is
            // the safe answer for a plugin that was just started: nobody is let in unchecked.
            connections.admitOnline(online);
        }
    }

    @Override
    public void close() {
        historyTask.cancel();
        if (countryTask != null) {
            countryTask.cancel();
        }
        try {
            countries.close();
        } catch (IOException unclosable) {
            plugin.getLogger().warning("Could not close the country file: " + unclosable.getMessage());
        }
        events.close();
        discord.close();
        staffing.close();
        filters.uninstall();
        hellos.close();
        web.close();
        mail.close();
        lane.close();
    }

    /** Fetches the country file when it is due and opens the book on it. */
    private CountryUpkeep countryUpkeep(Plugin plugin, PluginSettings settings, CountryBook book, Path folder) {
        return new CountryUpkeep(
                new CountryDownload(
                        web,
                        CountryDownload.MAXMIND,
                        folder,
                        () -> settings.current().protection().countries().licence(),
                        () -> settings.current().protection().countries().refresh(),
                        Clock.systemUTC(),
                        plugin.getLogger()::warning)::refresh,
                book,
                plugin.getLogger()::warning);
    }

    /** The door's rules, in order: the country, a banned address, then too many connections from one. */
    private static Admission admission(
            PluginSettings settings, AddressBans bans, CountryBook countries, java.util.logging.Logger logger) {
        return new Admission(
                () -> settings.current().protection().sharedAddresses(),
                List.of(
                        new CountryRule(
                                countries,
                                () -> settings.current()
                                        .protection()
                                        .countries()
                                        .rule(),
                                logger::warning),
                        bans.rule(),
                        new ConnectionWindow(
                                () -> settings.current().protection().connections(), Instant::now)));
    }

    /** The reader of the proxy's claims while this server is on a network, read from the settings of the moment. */
    private static Optional<Claims> claims(PluginSettings settings) {
        AuthSettings.Network network = settings.current().network();
        return network.enabled()
                ? Optional.of(new Claims(NetworkSecret.parse(network.secret()), network.claimFor()))
                : Optional.empty();
    }

    /** The bulletins of this server: on Redis while it is on a network that names one, and on itself otherwise. */
    private static NetworkEvents bulletins(PluginSettings settings, java.util.logging.Logger logger) {
        return claims(settings)
                .map(claims -> NetworkBulletins.open(
                        settings.current().network().redis(), new Bulletins(claims), logger::warning))
                .orElseGet(LocalEvents::new);
    }

    /** The decision at the hello: the web sources, the operator's rule, and the memories of claims and failures. */
    private AtTheDoor atTheDoor(
            PluginSettings settings, AccountRepository accounts, SqlDisputes disputes, Clock clock) {
        return new AtTheDoor(
                accounts,
                () -> settings.current().namePolicy(),
                lookup,
                () -> settings.current().ownership().decision(),
                new NameMemory(() -> settings.current().ownership().failedMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                disputes);
    }

    /** The console's move of every account to another database: one at a time, off the server's threads. */
    private static DatabaseDesk databaseDesk(
            Plugin plugin,
            Scheduler scheduler,
            PluginSettings settings,
            Database database,
            Replies replies,
            Messages messages) {
        MoveDatabase moves = new MoveDatabase(
                () -> plugin.getServer().getOnlinePlayers().size(),
                () -> settings.current()
                        .moveTo()
                        .map(to ->
                                TableCopy.open(database, to, settings.current().websiteTable(), plugin.getName())));
        SqlAccountRepository table = new SqlAccountRepository(database);
        ChangeScheme schemes = new ChangeScheme(
                table,
                table,
                new RecordedScheme(database),
                () -> plugin.getServer().getOnlinePlayers().size());
        return new DatabaseDesk(
                moves,
                schemes,
                task -> {
                    TaskHandle unused = scheduler.async(task);
                },
                replies,
                messages,
                plugin.getLogger()::info);
    }
}
