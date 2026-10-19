package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.adapter.outbound.hold.Passes;

/**
 * Moves a player who joins inside a wall or a portal to the nearest safe spot above: a quit in a block, or in a
 * portal the hold kept them from using, must not trap them at their next join. It runs on the join, on the
 * player's own thread, before the hold reads where they stand.
 */
public final class Rescue implements Listener {

    private static final Set<Material> PORTALS =
            EnumSet.of(Material.NETHER_PORTAL, Material.END_PORTAL, Material.END_GATEWAY);

    private final Supplier<AuthSettings.Spots> spots;
    private final Passes passes;
    private final BiFunction<Player, Location, CompletableFuture<Boolean>> mover;

    /**
     * @param passes the players this plugin is moving, which the hold lets through
     * @param mover how a player is moved, which a test without the server's teleport replaces
     */
    public Rescue(
            Supplier<AuthSettings.Spots> spots,
            Passes passes,
            BiFunction<Player, Location, CompletableFuture<Boolean>> mover) {
        this.spots = Objects.requireNonNull(spots, "spots");
        this.passes = Objects.requireNonNull(passes, "passes");
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        check(event.getPlayer());
    }

    /** Moves the player out when they stand in a wall or a portal and the rescue is on. */
    public void check(Player player) {
        Location here = player.getLocation();
        World world = here == null ? null : here.getWorld();
        if (here == null || world == null || !spots.get().rescue() || !trapped(world, here)) {
            return;
        }
        Location safe = safeAbove(world, here);
        UUID id = player.getUniqueId();
        passes.allow(id);
        CompletableFuture<Void> unused = mover.apply(player, safe).handle((moved, failure) -> {
            passes.done(id);
            return null;
        });
    }

    /**
     * Whether the body is in a full block or a portal. The feet are read half a block up and the head a block
     * and a half up, so a player standing on a slab or a carpet is not inside it.
     */
    private static boolean trapped(World world, Location here) {
        return blocks(world.getBlockAt(here.getBlockX(), (int) Math.floor(here.getY() + 0.5), here.getBlockZ())
                        .getType())
                || blocks(world.getBlockAt(here.getBlockX(), (int) Math.floor(here.getY() + 1.5), here.getBlockZ())
                        .getType());
    }

    private static boolean blocks(Material material) {
        return material.isOccluding() || PORTALS.contains(material);
    }

    /** The first spot above with room for a body and solid ground under it; the world's spawn when none is. */
    private static Location safeAbove(World world, Location here) {
        int x = here.getBlockX();
        int z = here.getBlockZ();
        for (int y = here.getBlockY() + 1; y < world.getMaxHeight() - 1; y++) {
            if (open(world.getBlockAt(x, y, z).getType())
                    && open(world.getBlockAt(x, y + 1, z).getType())
                    && world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                return at(here, y);
            }
        }
        return world.getSpawnLocation();
    }

    private static boolean open(Material material) {
        return !material.isSolid()
                && !PORTALS.contains(material)
                && material != Material.WATER
                && material != Material.LAVA;
    }

    private static Location at(Location here, int y) {
        return new Location(
                here.getWorld(), here.getBlockX() + 0.5, y, here.getBlockZ() + 0.5, here.getYaw(), here.getPitch());
    }
}
