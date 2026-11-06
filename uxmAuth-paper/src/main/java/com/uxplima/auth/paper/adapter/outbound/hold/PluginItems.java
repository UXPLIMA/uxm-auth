package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.bukkit.inventory.ItemStack;

/**
 * The items this plugin hands a held player: the map the captcha is drawn on and the map of the app's QR code.
 * They are the plugin's, not the player's, so the hold never keeps them as something that reached the player
 * while they waited. Each kind is added once its map exists, which is after the hold is built.
 */
public final class PluginItems implements Predicate<ItemStack> {

    private final List<Predicate<ItemStack>> kinds = new CopyOnWriteArrayList<>();

    /** Adds one kind of item this plugin hands out. */
    public void add(Predicate<ItemStack> kind) {
        kinds.add(Objects.requireNonNull(kind, "kind"));
    }

    @Override
    public boolean test(ItemStack item) {
        Objects.requireNonNull(item, "item");
        for (Predicate<ItemStack> kind : kinds) {
            if (kind.test(item)) {
                return true;
            }
        }
        return false;
    }
}
