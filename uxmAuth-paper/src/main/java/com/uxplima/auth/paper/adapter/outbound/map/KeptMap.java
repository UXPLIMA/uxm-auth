package com.uxplima.auth.paper.adapter.outbound.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import org.jspecify.annotations.Nullable;

/**
 * One map the server keeps for a picture of ours. Its number is written in the data folder and read back at the
 * next start, so no start leaves a map behind, and the renderer of this start replaces whatever drew it before.
 */
public final class KeptMap {

    private KeptMap() {}

    /**
     * The map numbered in this file, or a new one written there; empty when the server has no world to make one
     * in.
     *
     * @param what what the map shows, for the console
     */
    public static Optional<MapView> view(Server server, Path idFile, String what, Logger logger) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(idFile, "idFile");
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(logger, "logger");
        MapView kept = kept(server, idFile, what, logger);
        if (kept != null) {
            return Optional.of(kept);
        }
        if (server.getWorlds().isEmpty()) {
            logger.warning("No world is loaded to keep the " + what + " map in.");
            return Optional.empty();
        }
        World world = server.getWorlds().getFirst();
        MapView made = server.createMap(world);
        try {
            Files.writeString(idFile, String.valueOf(made.getId()));
        } catch (IOException failed) {
            logger.log(Level.WARNING, "The number of the " + what + " map could not be written to " + idFile, failed);
        }
        return Optional.of(made);
    }

    /** Hands the map to this renderer alone, fixed in place, so nothing of the world is drawn under it. */
    public static void claim(MapView view, MapRenderer renderer) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(renderer, "renderer");
        List.copyOf(view.getRenderers()).forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setLocked(true);
        view.addRenderer(renderer);
    }

    /** Whether this item is the map in this view. */
    public static boolean holds(ItemStack item, MapView view) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(view, "view");
        return item.getItemMeta() instanceof MapMeta meta
                && meta.hasMapView()
                && Objects.equals(meta.getMapView(), view);
    }

    /** A filled map item showing this view. */
    public static ItemStack item(MapView view) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) Objects.requireNonNull(item.getItemMeta(), "meta");
        meta.setMapView(Objects.requireNonNull(view, "view"));
        item.setItemMeta(meta);
        return item;
    }

    @SuppressWarnings("deprecation") // The number is the only handle a map keeps between starts.
    private static @Nullable MapView kept(Server server, Path idFile, String what, Logger logger) {
        if (!Files.isRegularFile(idFile)) {
            return null;
        }
        try {
            return server.getMap(Integer.parseInt(Files.readString(idFile).strip()));
        } catch (IOException | NumberFormatException unreadable) {
            logger.log(
                    Level.WARNING,
                    "The " + what + " map number in " + idFile + " could not be read; a new map is made",
                    unreadable);
            return null;
        }
    }
}
