package com.uxplima.auth.paper.adapter.outbound.world;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import com.uxplima.auth.adapter.outbound.ScreenSettings;
import org.jspecify.annotations.Nullable;

/**
 * The empty world a waiting player stands in while the waiting room is the login world.
 *
 * <p>It is made once, at enable, on the global thread, with one block to stand on. Folia cannot make a world
 * while it runs; there the operator makes it under the same name, and without it the players wait frozen.
 */
public final class LoginWorld {

    private static final int FLOOR = 64;
    private static final double CENTRE = 0.5;

    private final Server server;
    private final Supplier<ScreenSettings> settings;
    private final Logger logger;
    private final Function<WorldCreator, @Nullable World> creator;

    public LoginWorld(Server server, Supplier<ScreenSettings> settings, Logger logger) {
        this(server, settings, logger, server::createWorld);
    }

    /** @param creator how a world is made, which a test without a real server replaces */
    public LoginWorld(
            Server server,
            Supplier<ScreenSettings> settings,
            Logger logger,
            Function<WorldCreator, @Nullable World> creator) {
        this.server = Objects.requireNonNull(server, "server");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.creator = Objects.requireNonNull(creator, "creator");
    }

    /** Makes the world if the waiting room needs it and the server has none under its name. */
    public void prepare() {
        ScreenSettings now = settings.get();
        if (now.waitingRoom() != ScreenSettings.WaitingRoom.LOGIN_WORLD || server.getWorld(now.loginWorld()) != null) {
            return;
        }
        World made;
        try {
            made = creator.apply(new WorldCreator(now.loginWorld()).generator(new EmptyWorld()));
        } catch (UnsupportedOperationException cannot) {
            logger.warning("This server cannot make the login world " + now.loginWorld()
                    + " while it runs. Make it under that name, or players wait frozen where they joined.");
            return;
        }
        if (made != null) {
            made.getBlockAt(0, FLOOR, 0).setType(Material.BARRIER);
            made.setSpawnLocation(0, FLOOR + 1, 0);
        }
    }

    /** Where a waiting player stands, while the waiting room is the login world and the world is there. */
    public Optional<Location> room() {
        ScreenSettings now = settings.get();
        if (now.waitingRoom() != ScreenSettings.WaitingRoom.LOGIN_WORLD) {
            return Optional.empty();
        }
        World world = server.getWorld(now.loginWorld());
        return Optional.ofNullable(world == null ? null : new Location(world, CENTRE, FLOOR + 1, CENTRE));
    }
}
