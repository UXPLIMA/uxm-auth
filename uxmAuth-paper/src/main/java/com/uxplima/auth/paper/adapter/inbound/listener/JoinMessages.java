package com.uxplima.auth.paper.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.api.paper.event.LoginEvent;
import org.jspecify.annotations.Nullable;

/**
 * The server hears a player arrive once they are logged in, and never hears a stranger leave: a name nobody proved
 * is not announced, and one that never logged in did not really come.
 *
 * <p>The server's own join message, as every plugin before this one left it, is held at the join and said at the
 * login. A player logged in by the time the join message is written, by Mojang or a remembered session, keeps it.
 */
public final class JoinMessages implements Listener {

    private final BooleanSupplier waits;
    private final Predicate<UUID> loggedIn;
    private final Audience everybody;

    /** The join message each waiting player's login will say. */
    private final Map<UUID, Component> held = new ConcurrentHashMap<>();

    /**
     * @param waits whether the join message waits for the login, read at every join
     * @param loggedIn whether the player under an id is logged in now
     * @param everybody who hears a join message: every player and the console
     */
    public JoinMessages(BooleanSupplier waits, Predicate<UUID> loggedIn, Audience everybody) {
        this.waits = Objects.requireNonNull(waits, "waits");
        this.loggedIn = Objects.requireNonNull(loggedIn, "loggedIn");
        this.everybody = Objects.requireNonNull(everybody, "everybody");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Component message = event.joinMessage();
        if (message == null || !waits.getAsBoolean() || loggedIn.test(id)) {
            return;
        }
        held.compute(id, (key, before) -> message);
        event.joinMessage(null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(LoginEvent event) {
        Component message = take(event.getPlayer().getUniqueId());
        if (message != null) {
            everybody.sendMessage(message);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        if (take(event.getPlayer().getUniqueId()) != null) {
            event.quitMessage(null);
        }
    }

    private @Nullable Component take(UUID id) {
        AtomicReference<@Nullable Component> taken = new AtomicReference<>();
        held.compute(id, (key, message) -> {
            taken.set(message);
            return null;
        });
        return taken.get();
    }
}
