package com.uxplima.auth.paper.adapter.inbound.factor;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Keeps the QR map from outliving its setup. A dropped one vanishes and a death does not drop it, so it never lies
 * in the world; a join takes back one a crash left in an inventory; a quit ends the setup and takes it back. A
 * copy put in a chest shows nothing to anybody, because the picture is drawn only for its owner during a setup.
 */
public final class QrMapGuard implements Listener {

    private final Predicate<ItemStack> qr;
    private final Consumer<Player> takeBack;
    private final Consumer<Player> left;

    /**
     * @param qr whether an item is the QR map
     * @param takeBack takes every copy of the map from the player
     * @param left ends the player's setup and takes the map back
     */
    public QrMapGuard(Predicate<ItemStack> qr, Consumer<Player> takeBack, Consumer<Player> left) {
        this.qr = Objects.requireNonNull(qr, "qr");
        this.takeBack = Objects.requireNonNull(takeBack, "takeBack");
        this.left = Objects.requireNonNull(left, "left");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (qr.test(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(qr);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        takeBack.accept(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        left.accept(event.getPlayer());
    }
}
