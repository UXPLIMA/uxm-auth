package com.uxplima.auth.paper.adapter.inbound.listener;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * Ties a connection to a session: the account is read before the player is let in, the player is held at the
 * door when they join, and the session closes when they leave.
 */
public final class ConnectionListener implements Listener {

    private final Scheduler scheduler;
    private final Sessions sessions;
    private final AccountRepository accounts;
    private final Door door;
    private final HoldKeeper keeper;
    private final IntSupplier maxAttempts;
    private final Messages messages;
    private final Logger logger;

    public ConnectionListener(
            Scheduler scheduler,
            Sessions sessions,
            AccountRepository accounts,
            Door door,
            HoldKeeper keeper,
            IntSupplier maxAttempts,
            Messages messages,
            Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.door = Objects.requireNonNull(door, "door");
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Reads the account on the thread the server already gives this event, which is not a server thread. A
     * database that does not answer keeps the player out with a reason: let in, they would wait at a door
     * that can never open.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        try {
            sessions.open(event.getUniqueId(), waiting(new PlayerName(event.getName())));
        } catch (RuntimeException unreachable) {
            logger.log(
                    Level.WARNING,
                    "Cannot read the account of " + event.getName() + "; they were kept out",
                    unreachable);
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER, messages.render(Audience.empty(), Keys.KICK_STORAGE));
        }
    }

    /**
     * A player who was online before this plugin started has no session. Their account is read on the async
     * scheduler and they are sent to the door like one who just joined. A database that does not answer
     * kicks them with the same reason a joining player would read.
     */
    public void admitOnline(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerName name = new PlayerName(player.getName());
        scheduler.async(() -> {
            AuthState state;
            try {
                state = waiting(name);
            } catch (RuntimeException unreachable) {
                logger.log(Level.WARNING, "Cannot read the account of " + player.getName(), unreachable);
                scheduler.entity(player, () -> player.kick(messages.render(player, Keys.KICK_STORAGE)));
                return;
            }
            sessions.open(player.getUniqueId(), state);
            scheduler.entity(player, () -> door.hold(player));
        });
    }

    /** What a connection under this name waits for: its password, or an account. */
    private AuthState waiting(PlayerName name) {
        Optional<Account> account = accounts.findByName(name);
        return account.<AuthState>map(found -> new AuthState.Registered(found, Attempts.fresh(maxAttempts.getAsInt())))
                .orElseGet(() -> new AuthState.Unregistered(name));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        door.hold(event.getPlayer());
    }

    /** A player who leaves while held gets everything back before the server saves them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (state != null && !(state instanceof AuthState.Authenticated)) {
            keeper.leave(player);
        }
        sessions.close(player.getUniqueId());
    }
}
