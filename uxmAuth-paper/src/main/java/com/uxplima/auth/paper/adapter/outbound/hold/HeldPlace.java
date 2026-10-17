package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

/** Where a player stood before the hold moved them, by world name so it survives a restart. */
public record HeldPlace(String world, double x, double y, double z, float yaw, float pitch) {

    public HeldPlace {
        Objects.requireNonNull(world, "world");
    }

    /** The place of a location in a loaded world. */
    public static HeldPlace of(Location location) {
        Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(location.getWorld(), "world");
        return new HeldPlace(
                world.getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    /** The location again, while its world is loaded. */
    public Optional<Location> on(Server server) {
        World loaded = Objects.requireNonNull(server, "server").getWorld(world);
        return Optional.ofNullable(loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch));
    }
}
