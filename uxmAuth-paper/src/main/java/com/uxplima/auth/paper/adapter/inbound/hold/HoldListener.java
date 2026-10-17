package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
 * <p>A player is held unless this plugin holds a logged-in session for them. A player with no session at all
 * is held too: a missing session fails closed, because the way to one is a race this plugin must never lose
 * in the player's favour. The one exception is an NPC another plugin marks as one, which is a {@link Player}
 * to the server but never a person at the door.
 *
 * <p>Every handler runs on the event's own thread and reads only the concurrent session map, so it is safe on
 * Folia and on the chat thread.
 */
public final class HoldListener implements Listener {

    private final Sessions sessions;
    private final Predicate<UUID> passing;
    private final Supplier<AuthSettings.Hold> hold;
    private final Supplier<Set<String>> ownCommands;
    private final String namespace;

    /**
     * @param ownCommands every label and alias of this plugin's player commands, in lower case, which a
     *     waiting player must always be able to run
     * @param namespace the prefix the server puts before this plugin's commands, in lower case
     */
    public HoldListener(
            Sessions sessions, Supplier<AuthSettings.Hold> hold, Supplier<Set<String>> ownCommands, String namespace) {
        this(sessions, hold, ownCommands, namespace, player -> false);
    }

    /** @param passing whether a teleport of this player is the plugin's own, into the login world or back */
    public HoldListener(
            Sessions sessions,
            Supplier<AuthSettings.Hold> hold,
            Supplier<Set<String>> ownCommands,
            String namespace,
            Predicate<UUID> passing) {
        this.passing = Objects.requireNonNull(passing, "passing");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.hold = Objects.requireNonNull(hold, "hold");
        this.ownCommands = Objects.requireNonNull(ownCommands, "ownCommands");
        this.namespace = Objects.requireNonNull(namespace, "namespace").toLowerCase(Locale.ROOT);
    }

    /** Whether this player waits to log in. */
    public boolean isHeld(Player player) {
        AuthState state = sessions.state(player.getUniqueId()).orElse(null);
        if (state == null) {
            return !isNpc(player);
        }
        return !(state instanceof AuthState.Authenticated);
    }

    /** Citizens, the NPC plugin servers run most, marks every NPC it spawns with this metadata. */
    @SuppressWarnings("deprecation") // Paper deprecated metadata; Citizens still writes it.
    private static boolean isNpc(Player player) {
        return player.hasMetadata("NPC");
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

    /**
     * The older chat event, which Paper still fires for listeners written against it. A chat bridge or a log
     * that listens here would otherwise read a password a waiting player typed into chat by mistake.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    @SuppressWarnings("deprecation")
    public void onLegacyChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
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
        if (passing.test(event.getPlayer().getUniqueId())) {
            return;
        }
        stop(event, event.getPlayer(), AuthSettings.Hold::teleports);
    }

    private void stop(Cancellable event, Player player, Predicate<AuthSettings.Hold> switchedOn) {
        if (switchedOn.test(hold.get()) && isHeld(player)) {
            event.setCancelled(true);
        }
    }

    /**
     * Whether a waiting player may run this label. This plugin's own commands pass bare or under this plugin's
     * own namespace, never under another plugin's. An operator's allowed command is compared whole, so
     * {@code spawn} allows {@code /spawn} and only {@code essentials:spawn} allows {@code /essentials:spawn}.
     */
    private boolean allowed(String label) {
        int colon = label.indexOf(':');
        String bare = colon < 0 ? label : label.substring(colon + 1);
        boolean ours = colon < 0 || label.substring(0, colon).equals(namespace);
        return (ours && ownCommands.get().contains(bare))
                || hold.get().allowedCommands().stream().anyMatch(command -> command.equalsIgnoreCase(label));
    }

    /** The command's label in lower case, without its slash, with its namespace if it was written. */
    static String labelOf(String line) {
        String trimmed = line.strip();
        String withoutSlash = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        int space = withoutSlash.indexOf(' ');
        return (space < 0 ? withoutSlash : withoutSlash.substring(0, space)).toLowerCase(Locale.ROOT);
    }

    private static @Nullable Entity dealer(Entity damager) {
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }
}
