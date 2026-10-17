package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.GameMode;
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
 */
public final class HoldKeeper {

    private static final float STILL = 0.0f;

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final HeldStateStore store;
    private final Supplier<AuthSettings.Hold> hold;
    private final Predicate<Player> isHeld;
    private final Map<UUID, HeldState> taken = new ConcurrentHashMap<>();
    private final Set<UUID> blinded = ConcurrentHashMap.newKeySet();

    public HoldKeeper(
            Plugin plugin,
            Scheduler scheduler,
            HeldStateStore store,
            Supplier<AuthSettings.Hold> hold,
            Predicate<Player> isHeld) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.store = Objects.requireNonNull(store, "store");
        this.hold = Objects.requireNonNull(hold, "hold");
        this.isHeld = Objects.requireNonNull(isHeld, "isHeld");
    }

    /**
     * A player joined. Whatever a crash left on disk is given back first; then, if they wait to log in, their
     * state is written down and taken.
     */
    public void arrive(Player player) {
        Objects.requireNonNull(player, "player");
        UUID id = player.getUniqueId();
        scheduler.async(() -> {
            Optional<HeldState> leftover = store.load(id);
            scheduler.entity(player, () -> settle(player, leftover));
        });
    }

    /** The player logged in. Everything comes back on their own thread, and the file goes after. */
    public void giveBack(Player player) {
        Objects.requireNonNull(player, "player");
        scheduler.entity(player, () -> restore(player));
    }

    /**
     * A held player is leaving. Everything comes back now, on the quit's own thread, so the server saves the
     * player with their items and their operator status rather than without them.
     */
    public void leave(Player player) {
        Objects.requireNonNull(player, "player");
        restore(player);
    }

    private void settle(Player player, Optional<HeldState> leftover) {
        leftover.ifPresent(state -> apply(player, state));
        hideTheWaitingFrom(player);
        if (!isHeld.test(player)) {
            if (leftover.isPresent()) {
                scheduler.async(() -> store.delete(player.getUniqueId()));
            }
            return;
        }
        HeldState snapshot = capture(player, hold.get());
        taken.put(player.getUniqueId(), snapshot);
        scheduler.async(() -> {
            store.save(player.getUniqueId(), snapshot);
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
    }

    private void restore(Player player) {
        UUID id = player.getUniqueId();
        HeldState state = taken.remove(id);
        if (state != null) {
            apply(player, state);
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
        scheduler.async(() -> store.delete(id));
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

    private static HeldState capture(Player player, AuthSettings.Hold switches) {
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
                inventory);
    }

    private static void apply(Player player, HeldState state) {
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
    }

    private static double maxHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20.0 : max.getValue();
    }

    private static ItemStack orEmpty(@Nullable ItemStack item) {
        return item == null ? ItemStack.empty() : item;
    }
}
