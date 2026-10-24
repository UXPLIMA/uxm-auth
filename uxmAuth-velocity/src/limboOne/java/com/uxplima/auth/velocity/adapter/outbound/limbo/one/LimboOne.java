package com.uxplima.auth.velocity.adapter.outbound.limbo.one;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboKit;
import com.uxplima.auth.velocity.adapter.outbound.limbo.WorldFileKind;
import com.velocitypowered.api.command.CommandMeta;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.chunk.Dimension;
import net.elytrium.limboapi.api.chunk.VirtualBlock;
import net.elytrium.limboapi.api.chunk.VirtualWorld;
import net.elytrium.limboapi.api.command.LimboCommandMeta;
import net.elytrium.limboapi.api.file.BuiltInWorldFileType;
import net.elytrium.limboapi.api.player.GameMode;
import net.elytrium.limboapi.api.player.LimboPlayer;
import org.slf4j.Logger;

/** The waiting world on LimboAPI 1.1. Nobody can break or place a block in it. */
public final class LimboOne implements LimboKit {

    private static final String NAME = "uxmAuth";
    private static final int PLATFORM = 2;

    @Override
    public Room open(
            Object factory,
            Path folder,
            AuthSettings.Network settings,
            Collection<CommandMeta> commands,
            Logger logger) {
        LimboFactory limbos = (LimboFactory) Objects.requireNonNull(factory, "factory");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(commands, "commands");
        Objects.requireNonNull(logger, "logger");
        AuthSettings.Spot spawn = settings.limboSpawn();
        VirtualWorld world = limbos.createVirtualWorld(
                Dimension.OVERWORLD, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        if (!pasted(limbos, world, folder.resolve(settings.limboFile()), logger)) {
            platform(limbos, world, spawn);
        }
        Limbo limbo = limbos.createLimbo(world).setName(NAME).setGameMode(GameMode.ADVENTURE);
        for (CommandMeta command : commands) {
            // This generation takes its own meta for a command the limbo answers itself.
            limbo.registerCommand(new LimboCommandMeta(command.getAliases()));
        }
        return (player, guest) -> limbo.spawnPlayer(player, new Stay(guest));
    }

    private static boolean pasted(LimboFactory limbos, VirtualWorld world, Path file, Logger logger) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        Optional<WorldFileKind> kind = WorldFileKind.of(file.getFileName().toString());
        if (kind.isEmpty()) {
            logger.warn("{} is not a .schem, .schematic or .nbt file; the waiting world is a platform.", file);
            return false;
        }
        try {
            limbos.openWorldFile(BuiltInWorldFileType.valueOf(kind.get().name()), file)
                    .toWorld(limbos, world, 0, 0, 0);
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

    /** Hands what LimboAPI says of one player to the guest. */
    private record Stay(Guest guest) implements LimboSessionHandler {

        @Override
        public void onSpawn(Limbo server, LimboPlayer inWorld) {
            guest.arrive(new InWorld(inWorld));
        }

        @Override
        public void onChat(String line) {
            guest.said(line);
        }

        @Override
        public void onDisconnect() {
            guest.left();
        }
    }

    private record InWorld(LimboPlayer player) implements Inside {

        @Override
        public void stopFalling() {
            player.disableFalling();
        }

        @Override
        public ScheduledExecutorService loop() {
            return player.getScheduledExecutor();
        }

        @Override
        public void leave() {
            player.disconnect();
        }

        @Override
        public void draw(BufferedImage picture) {
            player.sendImage(picture);
        }
    }
}
