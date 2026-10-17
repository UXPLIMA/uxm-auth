package com.uxplima.auth.velocity.adapter.outbound.limbo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.velocitypowered.api.command.CommandMeta;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.world.BuiltInWorldFileType;
import net.elytrium.limboapi.api.world.VirtualWorld;
import net.elytrium.limboapi.api.world.chunk.Dimension;
import net.elytrium.limboapi.api.world.chunk.block.VirtualBlock;
import net.elytrium.limboapi.api.world.player.GameMode;
import org.slf4j.Logger;

/**
 * The world a waiting player stands in: the operator's schematic where it was saved, or a small platform under the
 * spawn when there is none. Nobody can break or place a block in it.
 */
public final class LimboWorld {

    private static final String NAME = "uxmAuth";
    private static final int PLATFORM = 2;

    private LimboWorld() {}

    /** The kind of world file this name is, by its extension. */
    public static Optional<BuiltInWorldFileType> kind(String file) {
        String name = Objects.requireNonNull(file, "file").toLowerCase(Locale.ROOT);
        if (name.endsWith(".schem")) {
            return Optional.of(BuiltInWorldFileType.WORLDEDIT_SCHEM);
        }
        if (name.endsWith(".schematic")) {
            return Optional.of(BuiltInWorldFileType.SCHEMATIC);
        }
        if (name.endsWith(".nbt")) {
            return Optional.of(BuiltInWorldFileType.STRUCTURE);
        }
        return Optional.empty();
    }

    /**
     * The waiting world, with the words for /login and /register known to the client so it sends them.
     *
     * @param factory the LimboAPI plugin, as the proxy hands it out
     */
    public static Limbo open(
            Object factory,
            Path folder,
            AuthSettings.Network settings,
            Collection<CommandMeta> commands,
            Logger logger) {
        LimboFactory limbos = (LimboFactory) Objects.requireNonNull(factory, "factory");
        AuthSettings.Spot spawn = settings.limboSpawn();
        VirtualWorld world = limbos.createVirtualWorld(
                Dimension.OVERWORLD, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        if (!pasted(limbos, world, folder.resolve(settings.limboFile()), logger)) {
            platform(limbos, world, spawn);
        }
        Limbo limbo = limbos.createLimbo(world).setName(NAME).setGameMode(GameMode.ADVENTURE);
        for (CommandMeta command : commands) {
            limbo.registerCommand(command);
        }
        return limbo;
    }

    private static boolean pasted(LimboFactory limbos, VirtualWorld world, Path file, Logger logger) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Optional<BuiltInWorldFileType> kind = kind(file.getFileName().toString());
        if (kind.isEmpty()) {
            logger.warn("{} is not a .schem, .schematic or .nbt file; the waiting world is a platform.", file);
            return false;
        }
        try {
            limbos.openWorldFile(kind.get(), file).toWorld(limbos, world, 0, 0, 0);
            return true;
        } catch (IOException | RuntimeException unreadable) {
            logger.warn("Cannot read {}; the waiting world is a platform.", file, unreadable);
            return false;
        }
    }

    private static void platform(LimboFactory limbos, VirtualWorld world, AuthSettings.Spot spawn) {
        VirtualBlock stone = limbos.createSimpleBlock("minecraft:stone");
        int x = (int) Math.floor(spawn.x());
        int y = (int) Math.floor(spawn.y()) - 1;
        int z = (int) Math.floor(spawn.z());
        for (int dx = -PLATFORM; dx <= PLATFORM; dx++) {
            for (int dz = -PLATFORM; dz <= PLATFORM; dz++) {
                world.setBlock(x + dx, y, z + dz, stone);
            }
        }
    }
}
