package com.uxplima.auth.paper.bootstrap;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LetBedrockIn;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.network.Claims;
import com.uxplima.auth.domain.network.NetworkSecret;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.ChangePasswordCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ClaimCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LoginCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LogoutCommand;
import com.uxplima.auth.paper.adapter.inbound.command.PlayerCommandLabels;
import com.uxplima.auth.paper.adapter.inbound.command.PremiumCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RegisterCommand;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.Greeter;
import com.uxplima.auth.paper.adapter.inbound.hold.HoldListener;
import com.uxplima.auth.paper.adapter.inbound.hold.Notices;
import com.uxplima.auth.paper.adapter.inbound.hold.WaitingClock;
import com.uxplima.auth.paper.adapter.inbound.hold.Welcomes;
import com.uxplima.auth.paper.adapter.inbound.listener.Arrivals;
import com.uxplima.auth.paper.adapter.inbound.listener.BedrockAdmission;
import com.uxplima.auth.paper.adapter.inbound.listener.ConnectionListener;
import com.uxplima.auth.paper.adapter.inbound.listener.HelloAtTheDoor;
import com.uxplima.auth.paper.adapter.inbound.listener.NameHolders;
import com.uxplima.auth.paper.adapter.inbound.listener.NetworkDoor;
import com.uxplima.auth.paper.adapter.inbound.screen.AuthScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.BedrockForms;
import com.uxplima.auth.paper.adapter.inbound.screen.ConfigurationGate;
import com.uxplima.auth.paper.adapter.inbound.screen.FormAnswers;
import com.uxplima.auth.paper.adapter.inbound.screen.InWorldScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.PaperScreens;
import com.uxplima.auth.paper.adapter.inbound.screen.PermissionDefaults;
import com.uxplima.auth.paper.adapter.inbound.screen.PlayerGuest;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenAnswers;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenClickListener;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenRouter;
import com.uxplima.auth.paper.adapter.inbound.screen.ScreenShower;
import com.uxplima.auth.paper.adapter.outbound.bedrock.BedrockLink;
import com.uxplima.auth.paper.adapter.outbound.client.DialogClients;
import com.uxplima.auth.paper.adapter.outbound.effect.AuthMoments;
import com.uxplima.auth.paper.adapter.outbound.handshake.PremiumHelloDoor;
import com.uxplima.auth.paper.adapter.outbound.handshake.ServerHellos;
import com.uxplima.auth.paper.adapter.outbound.hold.HeldStateStore;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.auth.paper.adapter.outbound.hold.Passes;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilter;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilters;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordMask;
import com.uxplima.auth.paper.adapter.outbound.world.LoginWorld;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;

/**
 * The way in, built once at enable: the sessions, the lane, the use cases, the hold, the door, the commands
 * and the log filter.
 *
 * <p>The hashing numbers and the lane sizes are read here and nowhere else, so a change to them applies at the
 * next start. Everything else is read from the settings on every use, so a reload reaches it.
 */
final class AuthGraph implements AutoCloseable {

    final Sessions sessions = new Sessions();
    final HashingLane lane;
    final ConnectionListener connections;
    final HoldListener hold;
    final LoginCommand login;
    final RegisterCommand register;
    final ChangePasswordCommand changePassword;
    final LogoutCommand logout;
    final ClaimCommand claim;
    final PremiumCommand premium;
    final ScreenClickListener screenClicks;
    final ConfigurationGate gate;
    private final LoginWorld loginWorld;
    private final BedrockLink bedrock;

    private final Plugin plugin;
    private final PasswordLogFilters filters;
    private final HttpClient web = HttpClient.newHttpClient();
    private final ServerHellos hellos;

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
        PluginSettings.Snapshot boot = settings.current();
        AuthSettings.Lane sizes = boot.lane();
        this.lane = new HashingLane(sizes.threads(), sizes.maxThreads(), sizes.queue());
        Hashing hashing = boot.hashers();
        SqlAccountRepository accounts = new SqlAccountRepository(database);
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
        AtTheDoor atTheDoor = atTheDoor(settings, accounts, disputes, clock);
        this.hellos = ServerHellos.startOn(new PremiumHelloDoor(atTheDoor, scheduler), plugin.getLogger());
        ClaimName claims = new ClaimName(
                sessions, atTheDoor, () -> settings.current().ownership().rule());
        AuthScreens screens = new AuthScreens(
                screenFile::current,
                () -> settings.current().passwords(),
                messages,
                styler,
                () -> hellos.installed() && claims.takesClaims());
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
        LogIn logIn = new LogIn(accounts, hashing, lane, sessions, clock);
        RegisterAccount registering = new RegisterAccount(
                accounts, hashing, lane, sessions, () -> settings.current().passwordPolicy(), clock);
        ScreenAnswers answers = new ScreenAnswers(
                screens,
                registering,
                logIn,
                screenFile::current,
                () -> settings.current().passwords(),
                messages,
                shower,
                id -> sessions.state(id).orElse(null) instanceof AuthState.Unregistered,
                claims::claim);
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
        Door door = new Door(
                sessions,
                keeper,
                waiting,
                moments,
                greeter,
                new InWorldScreens(
                        screenFile::current,
                        screens,
                        gate::sentToTheWorld,
                        (player, task) -> scheduler.entityLater(player, Duration.ofSeconds(1), task),
                        hold::isHeld,
                        shower),
                welcomes,
                notices);

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
                                        clock),
                                new Moves(accounts)),
                        messages,
                        scheduler,
                        plugin.getServer()::createProfile,
                        () -> settings.current().ownership().crackedMark(),
                        boot.ownership().uuidScheme(),
                        new NetworkDoor(() -> claims(settings), accounts::findById, clock, plugin.getLogger())),
                notices,
                door,
                keeper,
                new NameHolders(plugin.getServer(), gate::waitingFrom),
                () -> settings.current().names().reconnectFromSameAddress(),
                messages,
                plugin.getLogger());
        ConnectionListener joins = connections;
        this.login = new LoginCommand(logIn, door, joins::openingOf, moments, messages, replies);
        this.register = new RegisterCommand(
                registering, door, joins::openingOf, () -> settings.current().passwords(), messages, replies);
        this.changePassword = new ChangePasswordCommand(
                new ChangePassword(
                        accounts,
                        hashing,
                        lane,
                        sessions,
                        () -> settings.current().passwordPolicy()),
                moments,
                () -> settings.current().passwords(),
                messages,
                replies);
        this.logout = new LogoutCommand(
                new LogOut(sessions, () -> settings.current().passwords().maxAttempts()), door, moments, messages);
        this.claim = new ClaimCommand(claims, hellos::installed, messages);
        this.premium = new PremiumCommand(claims, hellos::installed, sessions, messages);
        this.screenClicks = new ScreenClickListener(
                answers, player -> new PlayerGuest(player, joins.openingOf(player), door, moments, replies), scheduler);
        formAnswers.set(screenClicks);
        this.filters = new PasswordLogFilters(new PasswordLogFilter(new PasswordMask(() -> labels)));
    }

    /** Listens, filters the log, and sends every player already online to the door. */
    void start() {
        loginWorld.prepare();
        bedrock.prefix()
                .filter(BedrockLink::collides)
                .ifPresent(prefix -> plugin.getLogger()
                        .warning("Floodgate's username-prefix is \"" + prefix
                                + "\". A Bedrock name with it can be a Java"
                                + " name too, and an unlinked Bedrock player could then reach a Java player's account."
                                + " Set it in Floodgate's config.yml to a character no Java name has, such as a dot."));
        plugin.getServer().getPluginManager().registerEvents(connections, plugin);
        plugin.getServer().getPluginManager().registerEvents(hold, plugin);
        plugin.getServer().getPluginManager().registerEvents(screenClicks, plugin);
        plugin.getServer().getPluginManager().registerEvents(gate, plugin);
        filters.install();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            // A player who was online before this enable has no session yet. They are asked again, which is
            // the safe answer for a plugin that was just started: nobody is let in unchecked.
            connections.admitOnline(online);
        }
    }

    @Override
    public void close() {
        filters.uninstall();
        hellos.close();
        web.close();
        lane.close();
    }

    /** The reader of the proxy's claims while this server is on a network, read from the settings of the moment. */
    private static Optional<Claims> claims(PluginSettings settings) {
        AuthSettings.Network network = settings.current().network();
        return network.enabled()
                ? Optional.of(new Claims(NetworkSecret.parse(network.secret()), network.claimFor()))
                : Optional.empty();
    }

    /** The decision at the hello: the web sources, the operator's rule, and the memories of claims and failures. */
    private AtTheDoor atTheDoor(
            PluginSettings settings, SqlAccountRepository accounts, SqlDisputes disputes, Clock clock) {
        PremiumLookup lookup = new PremiumLookup(
                new PremiumSources(web, () -> settings.current().premium(), clock)::ordered,
                () -> settings.current().premium().keepAnswers(),
                clock);
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
}
