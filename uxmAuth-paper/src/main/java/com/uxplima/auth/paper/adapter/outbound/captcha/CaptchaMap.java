package com.uxplima.auth.paper.adapter.outbound.captcha;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com.uxplima.auth.adapter.outbound.CaptchaImage;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.domain.protection.CaptchaPicture;
import com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen;
import org.jspecify.annotations.Nullable;

/**
 * The captcha on a map in the hand. The server keeps one map for it, whose number is written in the data folder
 * and read back at the next start, so no start leaves a map behind. The renderer draws each player the picture
 * of their own latest code, once per code.
 *
 * <p>The map goes only into a hand the hold emptied: the inventory is kept on disk while a player waits and comes
 * back whole at the login, so the map never takes the place of an item.
 */
public final class CaptchaMap extends MapRenderer implements CaptchaScreen.Hand {

    private final MapView view;
    private final Supplier<Captchas.Settings> settings;
    private final Predicate<UUID> bedrock;
    private final BooleanSupplier hidesInventory;
    private final RandomGenerator random;
    private final Map<UUID, BufferedImage> pictures = new ConcurrentHashMap<>();
    private final Map<UUID, BufferedImage> drawn = new ConcurrentHashMap<>();

    private CaptchaMap(
            MapView view,
            Supplier<Captchas.Settings> settings,
            Predicate<UUID> bedrock,
            BooleanSupplier hidesInventory,
            RandomGenerator random) {
        // Contextual: the canvas belongs to one player, so each sees their own code.
        super(true);
        this.view = view;
        this.settings = settings;
        this.bedrock = bedrock;
        this.hidesInventory = hidesInventory;
        this.random = random;
    }

    /**
     * The map of this server, taken over by a new renderer; empty when the server has no world to make one in,
     * and the captcha then goes on a screen.
     *
     * @param idFile where the number of the map is kept between starts
     * @param bedrock whether the player under this id is a Bedrock one, who reads a form instead
     * @param hidesInventory whether the hold empties a waiting player's inventory
     * @param random where the noise of each picture comes from
     */
    public static Optional<CaptchaMap> open(
            Server server,
            Path idFile,
            Supplier<Captchas.Settings> settings,
            Predicate<UUID> bedrock,
            BooleanSupplier hidesInventory,
            RandomGenerator random,
            Logger logger) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(logger, "logger");
        MapView view = view(server, Objects.requireNonNull(idFile, "idFile"), logger);
        if (view == null) {
            return Optional.empty();
        }
        CaptchaMap map = new CaptchaMap(
                view,
                Objects.requireNonNull(settings, "settings"),
                Objects.requireNonNull(bedrock, "bedrock"),
                Objects.requireNonNull(hidesInventory, "hidesInventory"),
                Objects.requireNonNull(random, "random"));
        List.copyOf(view.getRenderers()).forEach(view::removeRenderer);
        view.setTrackingPosition(false);
        view.setLocked(true);
        view.addRenderer(map);
        return Optional.of(map);
    }

    @Override
    public boolean give(Player player, String code) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        UUID id = player.getUniqueId();
        if (settings.get().java() != Captchas.JavaScreen.MAP || bedrock.test(id) || !hidesInventory.getAsBoolean()) {
            return false;
        }
        BufferedImage picture = CaptchaImage.of(CaptchaPicture.draw(code, random));
        pictures.compute(id, (key, before) -> picture);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) Objects.requireNonNull(item.getItemMeta(), "meta");
        meta.setMapView(view);
        item.setItemMeta(meta);
        player.getInventory().setItemInMainHand(item);
        return true;
    }

    @Override
    public void forget(UUID id) {
        Objects.requireNonNull(id, "id");
        pictures.compute(id, (key, before) -> null);
        drawn.compute(id, (key, before) -> null);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        UUID id = player.getUniqueId();
        BufferedImage picture = pictures.get(id);
        if (picture == null || picture == drawn.get(id)) {
            return;
        }
        canvas.drawImage(0, 0, picture);
        drawn.compute(id, (key, before) -> picture);
    }

    private static @Nullable MapView view(Server server, Path idFile, Logger logger) {
        MapView kept = kept(server, idFile, logger);
        if (kept != null) {
            return kept;
        }
        if (server.getWorlds().isEmpty()) {
            logger.warning("No world is loaded to keep the captcha map in, so the captcha goes on a screen.");
            return null;
        }
        World world = server.getWorlds().getFirst();
        MapView made = server.createMap(world);
        try {
            Files.writeString(idFile, String.valueOf(made.getId()));
        } catch (IOException failed) {
            logger.log(Level.WARNING, "The number of the captcha map could not be written to " + idFile, failed);
        }
        return made;
    }

    @SuppressWarnings("deprecation") // The number is the only handle a map keeps between starts.
    private static @Nullable MapView kept(Server server, Path idFile, Logger logger) {
        if (!Files.isRegularFile(idFile)) {
            return null;
        }
        try {
            return server.getMap(Integer.parseInt(Files.readString(idFile).strip()));
        } catch (IOException | NumberFormatException unreadable) {
            logger.log(
                    Level.WARNING,
                    "The captcha map number in " + idFile + " could not be read; a new map is made",
                    unreadable);
            return null;
        }
    }
}
