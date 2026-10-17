package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.List;
import java.util.Objects;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.Nullable;

/**
 * What the hold took from a player while they wait to log in, and gives back after.
 *
 * @param inventory every slot of the player's inventory in order, an empty stack for an empty slot, or no
 *     slots at all when the inventory was not hidden
 * @param place where the player stood before the hold moved them to the login world, or none
 */
public record HeldState(
        boolean op,
        boolean allowFlight,
        boolean flying,
        float walkSpeed,
        float flySpeed,
        double health,
        int food,
        GameMode gameMode,
        List<ItemStack> inventory,
        @Nullable HeldPlace place) {

    public HeldState {
        Objects.requireNonNull(gameMode, "gameMode");
        inventory = Objects.requireNonNull(inventory, "inventory").stream()
                .map(ItemStack::clone)
                .toList();
    }

    /** A state that did not move the player. */
    public HeldState(
            boolean op,
            boolean allowFlight,
            boolean flying,
            float walkSpeed,
            float flySpeed,
            double health,
            int food,
            GameMode gameMode,
            List<ItemStack> inventory) {
        this(op, allowFlight, flying, walkSpeed, flySpeed, health, food, gameMode, inventory, null);
    }

    /** A state that takes nothing, for a test or a player whose stats are not hidden. */
    public static HeldState empty(GameMode gameMode) {
        return new HeldState(false, false, false, 0.2f, 0.1f, 20.0, 20, gameMode, List.of());
    }
}
