package com.uxplima.auth.paper.adapter.outbound.factor;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import com.uxplima.auth.adapter.outbound.QrImage;
import com.uxplima.auth.domain.factor.QrMatrix;
import com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup;
import com.uxplima.auth.paper.adapter.outbound.map.KeptMap;

/**
 * The QR code of the setup link on a map, on the one {@link KeptMap} of the server for it. The renderer draws each
 * player the code of their own link, so the map shows nothing in anybody else's hands.
 *
 * <p>The map never takes an item's place: it goes into an empty hand, or the first empty hotbar slot, which is then
 * selected. A logged-in player keeps their inventory, so a map that went anywhere else would push an item out.
 */
public final class QrMap extends MapRenderer implements FactorSetup.Hand {

    private static final int HOTBAR = 9;

    private final MapView view;
    private final Logger logger;
    private final Map<UUID, BufferedImage> pictures = new ConcurrentHashMap<>();
    private final Map<UUID, BufferedImage> drawn = new ConcurrentHashMap<>();

    private QrMap(MapView view, Logger logger) {
        // Contextual: the canvas belongs to one player, so each sees their own code.
        super(true);
        this.view = view;
        this.logger = logger;
    }

    /** The QR map of this server, taken over by a new renderer; empty when no world is loaded to make one in. */
    public static Optional<QrMap> open(Server server, Path idFile, Logger logger) {
        Objects.requireNonNull(logger, "logger");
        return KeptMap.view(server, idFile, "QR code", logger).map(view -> {
            QrMap map = new QrMap(view, logger);
            KeptMap.claim(view, map);
            return map;
        });
    }

    @Override
    public boolean give(Player player, String link) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(link, "link");
        BufferedImage picture;
        try {
            picture = QrImage.of(QrMatrix.of(link));
        } catch (IllegalArgumentException tooLong) {
            logger.warning(tooLong.getMessage());
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        OptionalInt slot = slot(inventory);
        if (slot.isEmpty()) {
            return false;
        }
        take(player);
        UUID id = player.getUniqueId();
        pictures.compute(id, (key, before) -> picture);
        inventory.setItem(slot.getAsInt(), KeptMap.item(view));
        inventory.setHeldItemSlot(slot.getAsInt());
        return true;
    }

    /** Where the map goes: a hotbar slot that holds it already, the hand if empty, or the first empty one. */
    private OptionalInt slot(PlayerInventory inventory) {
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && holds(item)) {
                return OptionalInt.of(slot);
            }
        }
        if (inventory.getItemInMainHand().getType().isAir()) {
            return OptionalInt.of(inventory.getHeldItemSlot());
        }
        for (int slot = 0; slot < HOTBAR; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    @Override
    public void take(Player player) {
        Objects.requireNonNull(player, "player");
        PlayerInventory inventory = player.getInventory();
        // The contents hold the armour and the off hand too.
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item != null && holds(item)) {
                inventory.setItem(slot, null);
            }
        }
        if (holds(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
        UUID id = player.getUniqueId();
        pictures.compute(id, (key, before) -> null);
        drawn.compute(id, (key, before) -> null);
    }

    /** Whether this item is the QR map. */
    public boolean holds(ItemStack item) {
        return KeptMap.holds(Objects.requireNonNull(item, "item"), view);
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
