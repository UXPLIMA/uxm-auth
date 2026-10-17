package com.uxplima.auth.paper.adapter.outbound.hold;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * One file per held player, {@code held/<uuid>.conf}, written before anything is taken and deleted after it
 * is given back.
 *
 * <p>A server that dies while a player waits leaves the file behind, and the player's next join reads it and
 * gives back their operator status, their flight and their inventory. Without it a crash would keep them.
 *
 * <p>Every method blocks on the disk. The hold calls them from the async scheduler.
 */
public final class HeldStateStore {

    private final Path folder;

    public HeldStateStore(Path folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /** Writes the state whole: to a side file first, then moved over the real one in one step. */
    public void save(UUID player, HeldState state) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(state, "state");
        Path file = fileOf(player);
        Path side = folder.resolve(player + ".conf.tmp");
        try {
            Files.createDirectories(folder);
            HoconConfigurationLoader loader =
                    HoconConfigurationLoader.builder().path(side).build();
            CommentedConfigurationNode root = loader.createNode();
            write(root, state);
            loader.save(root);
            Files.move(side, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException unwritable) {
            throw new UncheckedIOException("cannot write the held state of " + player, unwritable);
        }
    }

    /**
     * The state held for this player, if any. A file that cannot be read is moved aside as {@code .broken}
     * for an operator to look at, rather than read as a state that takes nothing back.
     */
    public Optional<HeldState> load(UUID player) {
        Objects.requireNonNull(player, "player");
        Path file = fileOf(player);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    read(HoconConfigurationLoader.builder().path(file).build().load()));
        } catch (ConfigurateException | RuntimeException unreadable) {
            setAside(file);
            return Optional.empty();
        }
    }

    public void delete(UUID player) {
        Objects.requireNonNull(player, "player");
        try {
            Files.deleteIfExists(fileOf(player));
        } catch (IOException undeletable) {
            throw new UncheckedIOException("cannot delete the held state of " + player, undeletable);
        }
    }

    private Path fileOf(UUID player) {
        return folder.resolve(player + ".conf");
    }

    private static void write(ConfigurationNode root, HeldState state) throws ConfigurateException {
        root.node("op").set(state.op());
        root.node("allow-flight").set(state.allowFlight());
        root.node("flying").set(state.flying());
        root.node("walk-speed").set(state.walkSpeed());
        root.node("fly-speed").set(state.flySpeed());
        root.node("health").set(state.health());
        root.node("food").set(state.food());
        root.node("game-mode").set(state.gameMode().name());
        List<String> slots = new ArrayList<>();
        for (ItemStack item : state.inventory()) {
            slots.add(item.isEmpty() ? "" : Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        root.node("inventory").setList(String.class, slots);
    }

    private static HeldState read(ConfigurationNode root) throws ConfigurateException {
        List<ItemStack> inventory = new ArrayList<>();
        for (String slot : root.node("inventory").getList(String.class, List.of())) {
            inventory.add(
                    slot.isEmpty()
                            ? ItemStack.empty()
                            : ItemStack.deserializeBytes(Base64.getDecoder().decode(slot)));
        }
        return new HeldState(
                required(root, "op").getBoolean(),
                required(root, "allow-flight").getBoolean(),
                required(root, "flying").getBoolean(),
                required(root, "walk-speed").getFloat(),
                required(root, "fly-speed").getFloat(),
                required(root, "health").getDouble(),
                required(root, "food").getInt(),
                GameMode.valueOf(required(root, "game-mode").getString("").toUpperCase(Locale.ROOT)),
                inventory);
    }

    /** A value the file must hold. A file that lacks one is a file a crash cut short. */
    private static ConfigurationNode required(ConfigurationNode root, String name) {
        ConfigurationNode node = root.node(name);
        if (node.virtual() || node.raw() == null) {
            throw new IllegalStateException("the held state has no " + name);
        }
        return node;
    }

    private void setAside(Path file) {
        try {
            Files.move(file, file.resolveSibling(file.getFileName() + ".broken"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException unmovable) {
            throw new UncheckedIOException("cannot set aside the unreadable held state " + file, unmovable);
        }
    }
}
