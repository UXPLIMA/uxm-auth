package com.uxplima.auth.paper.adapter.outbound.hold;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.auth.adapter.outbound.AuthSettings;

/**
 * Where a waiting player stands while they log in, when the operator named a spot: read from the settings each
 * time, so a reload moves the next player, and found in the world only while the server has it loaded.
 */
public final class LoginSpot {

    private final Server server;
    private final Supplier<AuthSettings.Spots> spots;
    private final Logger logger;

    /** The worlds named and missing that the console has heard of already. */
    private final Set<String> told = ConcurrentHashMap.newKeySet();

    public LoginSpot(Server server, Supplier<AuthSettings.Spots> spots, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.spots = Objects.requireNonNull(spots, "spots");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /** The spot in its world, or empty when none is set or its world is not loaded. */
    public Optional<Location> where() {
        Optional<AuthSettings.Place> place = spots.get().loginSpot();
        if (place.isEmpty()) {
            return Optional.empty();
        }
        AuthSettings.Place spot = place.get();
        World world = server.getWorld(spot.world());
        if (world == null) {
            if (told.add(spot.world())) {
                logger.warning("location.login-spot names the world " + spot.world() + ", which this server has not"
                        + " loaded, so waiting players stay where they joined.");
            }
            return Optional.empty();
        }
        return Optional.of(new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()));
    }
}
