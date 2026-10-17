package com.uxplima.auth.paper.bootstrap;

import java.time.Clock;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.paper.adapter.inbound.command.ChangePasswordCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LoginCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LogoutCommand;
import com.uxplima.auth.paper.adapter.inbound.command.PlayerCommandLabels;
import com.uxplima.auth.paper.adapter.inbound.command.RegisterCommand;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.HoldListener;
import com.uxplima.auth.paper.adapter.inbound.hold.WaitingClock;
import com.uxplima.auth.paper.adapter.inbound.listener.ConnectionListener;
import com.uxplima.auth.paper.adapter.outbound.effect.AuthMoments;
import com.uxplima.auth.paper.adapter.outbound.hold.HeldStateStore;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilter;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordLogFilters;
import com.uxplima.auth.paper.adapter.outbound.log.PasswordMask;
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

    private final Plugin plugin;
    private final PasswordLogFilters filters;

    AuthGraph(
            Plugin plugin,
            Scheduler scheduler,
            PluginSettings settings,
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

        this.hold = new HoldListener(
                sessions,
                () -> settings.current().hold(),
                () -> labels,
                plugin.getName().toLowerCase(Locale.ROOT));
        HoldKeeper keeper = new HoldKeeper(
                plugin,
                scheduler,
                new HeldStateStore(plugin.getDataFolder().toPath().resolve("held")),
                () -> settings.current().hold(),
                hold::isHeld);
        AuthMoments moments = new AuthMoments(
                plugin.getServer(),
                scheduler,
                messages,
                styler,
                () -> settings.current().effects());
        WaitingClock waiting =
                new WaitingClock(scheduler, sessions, () -> settings.current().timeouts(), clock, moments, messages);
        Door door = new Door(sessions, keeper, waiting, moments);
        Replies replies = new Replies(scheduler, messages, plugin.getLogger());

        this.connections = new ConnectionListener(
                scheduler,
                sessions,
                accounts,
                door,
                keeper,
                () -> settings.current().passwords().maxAttempts(),
                messages,
                plugin.getLogger());
        this.login =
                new LoginCommand(new LogIn(accounts, hashing, lane, sessions, clock), door, moments, messages, replies);
        this.register = new RegisterCommand(
                new RegisterAccount(
                        accounts,
                        hashing,
                        lane,
                        sessions,
                        () -> settings.current().passwordPolicy(),
                        clock),
                door,
                () -> settings.current().passwords(),
                messages,
                replies);
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
        this.filters = new PasswordLogFilters(new PasswordLogFilter(new PasswordMask(() -> labels)));
    }

    /** Listens, filters the log, and sends every player already online to the door. */
    void start() {
        plugin.getServer().getPluginManager().registerEvents(connections, plugin);
        plugin.getServer().getPluginManager().registerEvents(hold, plugin);
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
        lane.close();
    }
}
