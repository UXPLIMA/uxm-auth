package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import io.papermc.paper.connection.PlayerConnection;

import net.kyori.adventure.audience.Audience;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.Notices;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.Messages;
import org.jspecify.annotations.Nullable;

/**
 * Ties a connection to a session: the account is read before the player is let in, the player is held at the
 * door when they join, and the session closes when they leave.
 */
public final class ConnectionListener implements Listener {

    private final Scheduler scheduler;
    private final Sessions sessions;
    private final Arrivals arrivals;
    private final Notices notices;
    private final Door door;
    private final HoldKeeper keeper;
    private final Function<UUID, Optional<InetAddress>> holders;
    private final BooleanSupplier sameAddress;
    private final Messages messages;
    private final Logger logger;

    /**
     * Which opening of a session each player object joined with. A quit closes that opening only: a second
     * connection under the same UUID opens its own before the server kicks the first, and the first one's
     * quit must not close the newcomer's session and leave them at no door at all.
     */
    private final Map<UUID, Joined> joined = new ConcurrentHashMap<>();

    /** A player object and the session generation it joined with. */
    private record Joined(Player player, long generation) {}

    /**
     * The connection that opened a session at the door, and which opening it made. It holds the name from the
     * door until it closes, whether or not it has a player yet, and a player is bound to it by address.
     */
    private record Opening(
            PlayerConnection connection, @Nullable InetSocketAddress address, long generation) {}

    private final Map<UUID, Opening> openings = new ConcurrentHashMap<>();

    public ConnectionListener(
            Scheduler scheduler,
            Sessions sessions,
            Arrivals arrivals,
            Notices notices,
            Door door,
            HoldKeeper keeper,
            Function<UUID, Optional<InetAddress>> holders,
            BooleanSupplier sameAddress,
            Messages messages,
            Logger logger) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.arrivals = Objects.requireNonNull(arrivals, "arrivals");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.door = Objects.requireNonNull(door, "door");
        this.keeper = Objects.requireNonNull(keeper, "keeper");
        this.holders = Objects.requireNonNull(holders, "holders");
        this.sameAddress = Objects.requireNonNull(sameAddress, "sameAddress");
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
        if (isHeldElsewhere(event.getUniqueId(), event.getAddress())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    messages.render(Audience.empty(), Keys.KICK_ALREADY_ONLINE));
            return;
        }
        try {
            switch (arrivals.arrive(event)) {
                case Arrivals.Arrival.Refused refused ->
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, refused.reason());
                case Arrivals.Arrival.Opens opens -> open(event, opens);
            }
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
     * Opens the session the arrival came to. A player who plays under another id than the one the server read is
     * held to one connection per name under that id too, and gets the profile that carries it.
     */
    private void open(AsyncPlayerPreLoginEvent event, Arrivals.Arrival.Opens opens) {
        UUID id = opens.id();
        if (!id.equals(event.getUniqueId()) && isHeldElsewhere(id, event.getAddress())) {
            event.disallow(
                    AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    messages.render(Audience.empty(), Keys.KICK_ALREADY_ONLINE));
            return;
        }
        PlayerProfile profile = opens.profile();
        if (profile != null) {
            event.setPlayerProfile(profile);
        }
        sessions.open(id, opens.state());
        for (Arrivals.Notice notice : opens.notices()) {
            if (notice.afterLogin()) {
                notices.onceIn(id, notice.notice());
            } else {
                notices.whileWaiting(id, notice.notice());
            }
        }
        remember(event, id);
    }

    /**
     * One connection per name: a second one would open the session again, and the first connection's login
     * would let the second one in. The operator may let a reconnect through from the same address, for a
     * player whose first connection hung.
     */
    private boolean isHeldElsewhere(UUID id, InetAddress from) {
        Optional<InetAddress> holder = openedFrom(id).or(() -> holders.apply(id));
        return holder.isPresent()
                && !(sameAddress.getAsBoolean() && holder.get().equals(from));
    }

    private void remember(AsyncPlayerPreLoginEvent event, UUID id) {
        PlayerConnection connection = event.getConnection();
        sessions.held(id).ifPresent(held -> {
            if (connection != null) {
                openings.put(id, new Opening(connection, connection.getClientAddress(), held.generation()));
            }
        });
    }

    /** The address of a live connection that opened this id at the door; a closed one is forgotten here. */
    private Optional<InetAddress> openedFrom(UUID id) {
        Opening opening = openings.get(id);
        if (opening == null) {
            return Optional.empty();
        }
        if (!opening.connection().isConnected()) {
            openings.remove(id, opening);
            return Optional.empty();
        }
        InetSocketAddress address = opening.address();
        return Optional.of(address == null ? InetAddress.getLoopbackAddress() : address.getAddress());
    }

    /** The opening this player stands in the world under, empty until they joined with one. */
    public OptionalLong openingOf(Player player) {
        Joined mine = joined.get(Objects.requireNonNull(player, "player").getUniqueId());
        return mine == null || mine.player() != player ? OptionalLong.empty() : OptionalLong.of(mine.generation());
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
                state = arrivals.waiting(name);
            } catch (RuntimeException unreachable) {
                logger.log(Level.WARNING, "Cannot read the account of " + player.getName(), unreachable);
                scheduler.entity(player, () -> player.kick(messages.render(player, Keys.KICK_STORAGE)));
                return;
            }
            sessions.open(player.getUniqueId(), state);
            sessions.held(player.getUniqueId())
                    .ifPresent(held -> joined.put(player.getUniqueId(), new Joined(player, held.generation())));
            scheduler.entity(player, () -> door.hold(player));
        });
    }

    /** A player with a session goes to the door; one without is read again and held meanwhile. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Sessions.Held held = sessions.held(player.getUniqueId()).orElse(null);
        if (held == null) {
            admitOnline(player);
            return;
        }
        if (isReplaced(player, held)) {
            // A later connection under this name opened the session; this one must not stand in for it.
            scheduler.entity(player, () -> player.kick(messages.render(player, Keys.KICK_ALREADY_ONLINE)));
            return;
        }
        joined.put(player.getUniqueId(), new Joined(player, held.generation()));
        door.hold(player);
    }

    private boolean isReplaced(Player player, Sessions.Held held) {
        Opening opening = openings.get(player.getUniqueId());
        if (opening == null) {
            return false;
        }
        return !Objects.equals(opening.address(), player.getAddress()) || opening.generation() != held.generation();
    }

    /**
     * The client said its language. The greeting runs on the next turn of the player's own thread, after the
     * listener that writes the language down has run on this event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocale(PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        scheduler.entity(player, () -> door.languageKnown(player));
    }

    /**
     * A connection closed. The event names an id, not a connection, so only an opening whose own connection is
     * gone is forgotten, and its session with it when that connection never stood in the world: one that left
     * at the dialog, or before it, would otherwise hold its session for as long as the server runs.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(PlayerConnectionCloseEvent event) {
        UUID id = event.getPlayerUniqueId();
        Opening opening = openings.get(id);
        if (opening == null || opening.connection().isConnected() || !openings.remove(id, opening)) {
            return;
        }
        Joined mine = joined.get(id);
        if (mine == null || mine.generation() != opening.generation()) {
            sessions.close(id, opening.generation());
        }
    }

    /** How many openings are remembered, for a test that proves they are let go. */
    int openings() {
        return openings.size();
    }

    /** A player who leaves while held gets everything back before the server saves them. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        door.leave(player);
        Joined mine = joined.get(id);
        if (mine == null || mine.player() != player) {
            return;
        }
        joined.remove(id, mine);
        Sessions.Held held = sessions.held(id).orElse(null);
        if (held != null
                && held.generation() == mine.generation()
                && !(held.state() instanceof AuthState.Authenticated)) {
            keeper.leave(player);
        }
        sessions.close(id, mine.generation());
    }
}
