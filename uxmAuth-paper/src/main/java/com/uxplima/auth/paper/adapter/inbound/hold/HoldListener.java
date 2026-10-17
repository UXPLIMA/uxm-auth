package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Cancels what a waiting player tries to do, one switch of {@code hold} at a time.
 *
 * <p>A player is held while this plugin holds a session for them that is not logged in. A player with no
 * session at all is left alone: an NPC from another plugin is a {@link Player} too, and freezing it would
 * break that plugin.
 *
 * <p>Every handler runs on the event's own thread and reads only the concurrent session map, so it is safe on
 * Folia and on the chat thread.
 */
public final class HoldListener implements Listener {

    private final Sessions sessions;
    private final Supplier<AuthSettings.Hold> hold;
    private final Supplier<Set<String>> ownCommands;

    /**
     * @param ownCommands every label and alias of this plugin's player commands, in lower case, which a
     *     waiting player must always be able to run
     */
    public HoldListener(Sessions sessions, Supplier<AuthSettings.Hold> hold, Supplier<Set<String>> ownCommands) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.hold = Objects.requireNonNull(hold, "hold");
        this.ownCommands = Objects.requireNonNull(ownCommands, "ownCommands");
    }

    /** Whether this player waits to log in. */
    public boolean isHeld(Player player) {
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        return state != null && !(state instanceof AuthState.Authenticated);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean walked = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        if (walked) {
            stop(event, event.getPlayer(), AuthSettings.Hold::movement);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::chat);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!allowed(labelOf(event.getMessage()))) {
            stop(event, event.getPlayer(), AuthSettings.Hold::commands);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::blocks);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::blocks);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::interact);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::interact);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player) {
            stop(event, player, AuthSettings.Hold::inventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            stop(event, player, AuthSettings.Hold::inventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            stop(event, player, AuthSettings.Hold::inventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::inventory);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            stop(event, player, AuthSettings.Hold::inventory);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        stop(event, event.getPlayer(), AuthSettings.Hold::inventory);
    }

    /** Damage taken by a waiting player, and damage dealt by one, with a hand or with something they shot. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player taken) {
            stop(event, taken, AuthSettings.Hold::damage);
        }
        if (event instanceof EntityDamageByEntityEvent byEntity
                && dealer(byEntity.getDamager()) instanceof Player dealt) {
            stop(event, dealt, AuthSettings.Hold::damage);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) {
            stop(event, player, AuthSettings.Hold::vehicles);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent) {
            stop(event, event.getPlayer(), AuthSettings.Hold::portals);
            return;
        }
        stop(event, event.getPlayer(), AuthSettings.Hold::teleports);
    }

    private void stop(Cancellable event, Player player, Predicate<AuthSettings.Hold> switchedOn) {
        if (switchedOn.test(hold.get()) && isHeld(player)) {
            event.setCancelled(true);
        }
    }

    private boolean allowed(String label) {
        return ownCommands.get().contains(label)
                || hold.get().allowedCommands().stream().anyMatch(command -> command.equalsIgnoreCase(label));
    }

    /** The command's label in lower case, without its slash and without a plugin's namespace. */
    static String labelOf(String line) {
        String trimmed = line.strip();
        String withoutSlash = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        int space = withoutSlash.indexOf(' ');
        String label = space < 0 ? withoutSlash : withoutSlash.substring(0, space);
        int colon = label.indexOf(':');
        return (colon < 0 ? label : label.substring(colon + 1)).toLowerCase(Locale.ROOT);
    }

    private static @Nullable Entity dealer(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }
}
