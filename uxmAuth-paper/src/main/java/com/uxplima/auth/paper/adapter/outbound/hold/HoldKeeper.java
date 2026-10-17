package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.uxmlib.scheduler.Scheduler;
import org.jspecify.annotations.Nullable;

/**
 * Takes from a waiting player what the hold hides, and gives it back.
 *
 * <p>What is taken is written to disk before it is taken, so a crash never keeps it: the next join reads the
 * file and gives it back first. The disk is touched on the async scheduler and the player only on their own
 * thread, so this is safe on Folia.
 *
 * <p>The disk work of one player runs in one chain, in the order it was asked for. Two async tasks are
 * otherwise free to finish in any order, and a write that landed after the delete of a quick login would
 * leave a file that hands back an old inventory and an old operator status at some later join.
 */
public final class HoldKeeper {

    private static final float STILL = 0.0f;

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final HeldStateStore store;
    private final Supplier<AuthSettings.Hold> hold;
    private final Predicate<Player> isHeld;
    private final Supplier<Optional<Location>> room;
    private final Passes passes;
    private final BiFunction<Player, Location, CompletableFuture<Boolean>> mover;
    private final Map<UUID, HeldState> taken = new ConcurrentHashMap<>();
    private final Set<UUID> blinded = ConcurrentHashMap.newKeySet();
    private final Map<UUID, CompletableFuture<Void>> disk = new ConcurrentHashMap<>();

    public HoldKeeper(
            Plugin plugin,
            Scheduler scheduler,
            HeldStateStore store,
            Supplier<AuthSettings.Hold> hold,
            Predicate<Player> isHeld) {
        this(plugin, scheduler, store, hold, isHeld, Optional::empty, new Passes());
    }

    /**
     * @param room where a waiting player is moved while the waiting room is the login world, or empty
     * @param passes the players this keeper is moving, which the hold lets through
     */
    public HoldKeeper(
            Plugin plugin,
            Scheduler scheduler,
            HeldStateStore store,
            Supplier<AuthSettings.Hold> hold,
            Predicate<Player> isHeld,
            Supplier<Optional<Location>> room,
            Passes passes) {
        this(plugin, scheduler, store, hold, isHeld, room, passes, Player::teleportAsync);
    }

    /** @param mover how a player is moved, which a test without the server's teleport replaces */
    public HoldKeeper(
            Plugin plugin,
            Scheduler scheduler,
            HeldStateStore store,
            Supplier<AuthSettings.Hold> hold,
            Predicate<Player> isHeld,
            Supplier<Optional<Location>> room,
            Passes passes,
            BiFunction<Player, Location, CompletableFuture<Boolean>> mover) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.store = Objects.requireNonNull(store, "store");
        this.hold = Objects.requireNonNull(hold, "hold");
        this.isHeld = Objects.requireNonNull(isHeld, "isHeld");
        this.room = Objects.requireNonNull(room, "room");
        this.passes = Objects.requireNonNull(passes, "passes");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    /**
     * A player joined. Whatever a crash left on disk is given back first; then, if they wait to log in, their
     * state is written down and taken.
     */
    public void arrive(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        onDisk(id, () -> {
            Optional<HeldState> leftover = store.load(id);
            Optional<HeldPlace> left = store.loadPlace(id);
            scheduler.entity(player, () -> settle(player, leftover, left));
        });
    }

    /** The player logged in. Everything comes back on their own thread, and the file goes after. */
    public void giveBack(Player player) {
        Objects.requireNonNull(player, "player");
        scheduler.entity(player, () -> restore(player, false));
    }

    /**
     * A held player is leaving. Everything comes back now, on the quit's own thread, so the server saves the
     * player with their items and their operator status rather than without them.
     */
    public void leave(Player player) {
        Objects.requireNonNull(player, "player");
        restore(player, true);
    }

    private void settle(Player player, Optional<HeldState> leftover, Optional<HeldPlace> left) {
        UUID id = player.getUniqueId();
        if (!player.isOnline()) {
            // Gone before the hold was set up. A leftover stays on disk for their next join.
            return;
        }
        leftover.ifPresent(state -> apply(player, state, true));
        hideTheWaitingFrom(player);
        if (!isHeld.test(player)) {
            left.flatMap(place -> place.on(plugin.getServer())).ifPresent(target -> move(player, target));
            if (leftover.isPresent() || left.isPresent()) {
                onDisk(id, () -> {
                    store.delete(id);
                    store.deletePlace(id);
                });
            }
            return;
        }
        // A player still on their way back from the login world stood where the leftover says, not here.
        HeldPlace place = room.get().isEmpty() ? null : placeOf(player, leftover, left);
        HeldState snapshot = capture(player, hold.get(), place);
        taken.put(id, snapshot);
        onDisk(id, () -> {
            // Given back already: the player is whole and a file now would only be a stale one.
            if (taken.get(id) != snapshot) {
                return;
            }
            store.save(id, snapshot);
            store.deletePlace(id);
            scheduler.entity(player, () -> strip(player));
        });
    }

    /** Takes what the switches say, if the player still waits. A login that came first leaves them whole. */
    private void strip(Player player) {
        if (!player.isOnline() || !isHeld.test(player)) {
            return;
        }
        AuthSettings.Hold switches = hold.get();
        if (switches.hideStats()) {
            player.setOp(false);
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setWalkSpeed(STILL);
            player.setFlySpeed(STILL);
            player.setGameMode(GameMode.ADVENTURE);
        }
        if (switches.hideInventory()) {
            player.getInventory().clear();
        }
        if (switches.blindness()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.BLINDNESS, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            blinded.add(player.getUniqueId());
        }
        if (switches.hidePlayers()) {
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) {
                    player.hidePlayer(plugin, other);
                    scheduler.entity(other, () -> other.hidePlayer(plugin, player));
                }
            }
        }
        room.get().ifPresent(target -> move(player, target));
    }

    /**
     * Gives back what was taken. A player leaving from the login world is not moved on the way out, which a
     * quitting player cannot always finish; the file stays, and their next join moves them back.
     */
    private void restore(Player player, boolean leaving) {
        UUID id = player.getUniqueId();
        HeldState state = taken.remove(id);
        if (state != null) {
            apply(player, state, !leaving);
        }
        if (blinded.remove(id)) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player) && !isHeld.test(other)) {
                player.showPlayer(plugin, other);
                scheduler.entity(other, () -> other.showPlayer(plugin, player));
            }
        }
        HeldPlace place = state == null ? null : state.place();
        if (leaving && place != null) {
            // Only the place outlives the quit; everything else came back just now.
            onDisk(id, () -> {
                store.savePlace(id, place);
                store.delete(id);
            });
        } else {
            onDisk(id, () -> store.delete(id));
        }
    }

    private static @Nullable HeldPlace placeOf(Player player, Optional<HeldState> leftover, Optional<HeldPlace> left) {
        HeldPlace before = leftover.isPresent() ? leftover.get().place() : null;
        if (before != null) {
            return before;
        }
        if (left.isPresent()) {
            return left.get();
        }
        Location here = player.getLocation();
        return here == null || here.getWorld() == null ? null : HeldPlace.of(here);
    }

    /** Moves a player as the hold's own teleport, which the hold lets through. */
    private void move(Player player, Location target) {
        UUID id = player.getUniqueId();
        passes.allow(id);
        CompletableFuture<Void> unused = mover.apply(player, target).handle((moved, failure) -> {
            passes.done(id);
            return null;
        });
    }

    /**
     * Runs disk work for one player after the disk work asked for before it, on the async scheduler.
     *
     * <p>Each call swaps in a gate of its own and waits on the gate it replaced, so the order of the calls is
     * the order of the work. The gate opens when the work is done, whether it failed or not.
     */
    private void onDisk(UUID id, Runnable work) {
        CompletableFuture<Void> gate = new CompletableFuture<>();
        CompletableFuture<Void> before = disk.put(id, gate);
        CompletableFuture<Void> unused = (before == null ? CompletableFuture.<Void>completedFuture(null) : before)
                .thenRunAsync(work, scheduler::async)
                .handle((done, failure) -> {
                    if (failure != null) {
                        plugin.getLogger()
                                .log(
                                        Level.WARNING,
                                        "The held state of " + id + " could not be read or written",
                                        failure);
                    }
                    disk.remove(id, gate);
                    gate.complete(null);
                    return null;
                });
    }

    /** A newcomer and every player who waits do not see each other. */
    private void hideTheWaitingFrom(Player newcomer) {
        if (!hold.get().hidePlayers()) {
            return;
        }
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(newcomer) && isHeld.test(other)) {
                newcomer.hidePlayer(plugin, other);
                scheduler.entity(other, () -> other.hidePlayer(plugin, newcomer));
            }
        }
    }

    private static HeldState capture(Player player, AuthSettings.Hold switches, @Nullable HeldPlace place) {
        List<ItemStack> inventory = switches.hideInventory()
                ? Arrays.stream(player.getInventory().getContents())
                        .map(HoldKeeper::orEmpty)
                        .toList()
                : List.of();
        return new HeldState(
                player.isOp(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getGameMode(),
                inventory,
                place);
    }

    private void apply(Player player, HeldState state, boolean moveBack) {
        player.setOp(state.op());
        player.setGameMode(state.gameMode());
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.allowFlight() && state.flying());
        player.setWalkSpeed(state.walkSpeed());
        player.setFlySpeed(state.flySpeed());
        player.setHealth(Math.min(state.health(), maxHealth(player)));
        player.setFoodLevel(state.food());
        if (!state.inventory().isEmpty()) {
            player.getInventory().setContents(state.inventory().toArray(ItemStack[]::new));
        }
        HeldPlace place = state.place();
        if (moveBack && place != null) {
            place.on(plugin.getServer()).ifPresent(target -> move(player, target));
        }
    }

    private static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20.0 : max.getValue();
    }

    private static ItemStack orEmpty(@Nullable ItemStack item) {
        return item == null ? ItemStack.empty() : item;
    }
}
