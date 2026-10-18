package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Remembering;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * {@code /session on|off}: a logged-in player chooses whether a join from the same address skips the password,
 * while the operator leaves sessions to the player's choice. On remembers this login at once; off ends every
 * session the account has.
 */
@FromConfig(
        value = SessionCommand.KEY,
        fallbackName = "session",
        description = "Choose whether this address skips the password next time")
public final class SessionCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "session";

    private final Remember remember;
    private final Remembering remembering;
    private final Sessions sessions;
    private final Supplier<Remember.Settings> settings;
    private final Scheduler scheduler;
    private final Messages messages;

    public SessionCommand(
            Remember remember,
            Remembering remembering,
            Sessions sessions,
            Supplier<Remember.Settings> settings,
            Scheduler scheduler,
            Messages messages) {
        this.remember = Objects.requireNonNull(remember, "remember");
        this.remembering = Objects.requireNonNull(remembering, "remembering");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Subcommand("")
    @Permission("uxmauth.session")
    @PlayerOnly
    public void usage(Sender sender) {
        messages.send((Player) sender.bukkit(), Keys.SESSION_USAGE);
    }

    @FromConfig(value = "on", fallbackName = "on", description = "Skip the password from this address next time")
    @Permission("uxmauth.session")
    @PlayerOnly
    public void on(Sender sender) {
        attempt((Player) sender.bukkit(), true);
    }

    @FromConfig(value = "off", fallbackName = "off", description = "Ask the password at every join")
    @Permission("uxmauth.session")
    @PlayerOnly
    public void off(Sender sender) {
        attempt((Player) sender.bukkit(), false);
    }

    void attempt(Player player, boolean on) {
        if (!(sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.Authenticated in)) {
            messages.send(player, Keys.SESSION_LOG_IN_FIRST);
            return;
        }
        Remember.Settings now = settings.get();
        if (now.mode() != Remember.Mode.CHOSEN) {
            messages.send(player, Keys.SESSION_NOT_CHOSEN);
            return;
        }
        sessions.chose(player.getUniqueId(), on);
        scheduler.async(() -> remember.choose(in.account().id(), on));
        if (on) {
            // This login is written as though the box had been ticked on its screen.
            remembering.loggedIn(player, in);
            messages.send(
                    player,
                    Keys.SESSION_ON,
                    Text.placeholder("minutes", String.valueOf(now.lasts().toMinutes())));
        } else {
            messages.send(player, Keys.SESSION_OFF);
        }
    }
}
