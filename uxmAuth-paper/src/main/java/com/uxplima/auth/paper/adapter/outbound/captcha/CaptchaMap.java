package com.uxplima.auth.paper.adapter.outbound.captcha;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com.uxplima.auth.adapter.outbound.CaptchaImage;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.domain.protection.CaptchaPicture;
import com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen;
import com.uxplima.auth.paper.adapter.outbound.map.KeptMap;

/**
 * The captcha on a map in the hand, on the one {@link KeptMap} of the server for it. The renderer draws each player
 * the picture of their own latest code, once per code.
 *
 * <p>The map goes only into a hand the hold emptied, or one that holds this map already: the inventory is kept on
 * disk while a player waits and comes back whole at the login, and an item still in the hand was not taken yet, so
 * the map never takes the place of an item.
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
        Optional<MapView> view = KeptMap.view(server, Objects.requireNonNull(idFile, "idFile"), "captcha", logger);
        if (view.isEmpty()) {
            return Optional.empty();
        }
        CaptchaMap map = new CaptchaMap(
                view.get(),
                Objects.requireNonNull(settings, "settings"),
                Objects.requireNonNull(bedrock, "bedrock"),
                Objects.requireNonNull(hidesInventory, "hidesInventory"),
                Objects.requireNonNull(random, "random"));
        KeptMap.claim(view.get(), map);
        return Optional.of(map);
    }

    @Override
    public boolean give(Player player, String code) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(code, "code");
        UUID id = player.getUniqueId();
        if (settings.get().java() != Captchas.JavaScreen.MAP
                || bedrock.test(id)
                || !hidesInventory.getAsBoolean()
                || !freeHand(player)) {
            return false;
        }
        BufferedImage picture = CaptchaImage.of(CaptchaPicture.draw(code, random));
        pictures.compute(id, (key, before) -> picture);
        player.getInventory().setItemInMainHand(KeptMap.item(view));
        return true;
    }

    /**
     * Whether the hand holds nothing, or this map: anything else is an item the hold has not taken yet, and a map
     * in its place would lose it.
     */
    private boolean freeHand(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        return held.getType().isAir() || KeptMap.holds(held, view);
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
}
