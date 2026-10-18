package com.uxplima.auth.paper.adapter.outbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.map.MapView;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.uxplima.auth.paper.adapter.outbound.map.KeptMap;
import com.uxplima.auth.paper.testing.MapServer;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The QR code of the setup link on a map, put where it takes no item's place and taken back after. */
final class QrMapTest {

    private static final String LINK =
            "otpauth://totp/Uxplima:Steve?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ&issuer=Uxplima";

    @TempDir
    Path folder;

    private ServerMock server;
    private PlayerMock steve;
    private @Nullable MapServer maps;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        steve = server.addPlayer("Steve");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private QrMap map() {
        if (maps == null) {
            maps = new MapServer(server);
        }
        return QrMap.open(maps.server(), folder.resolve("qr-map.id"), Logger.getLogger("qr-test"))
                .orElseThrow();
    }

    private static String read(BufferedImage picture) throws Exception {
        int[] pixels = picture.getRGB(0, 0, 128, 128, null, 0, 128);
        return new QRCodeReader()
                .decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(128, 128, pixels))))
                .getText();
    }

    private static MapView viewOf(ItemStack item) {
        assertThat(item.getType()).isEqualTo(Material.FILLED_MAP);
        return Objects.requireNonNull(((org.bukkit.inventory.meta.MapMeta) item.getItemMeta()).getMapView());
    }

    private int copies(QrMap map) {
        int found = 0;
        for (ItemStack item : steve.getInventory().getContents()) {
            if (item != null && map.holds(item)) {
                found++;
            }
        }
        return found;
    }

    @Test
    @DisplayName("an empty hand takes the map, and the player is drawn the QR code of their own link")
    void anEmptyHandTakesTheMap() throws Exception {
        QrMap map = map();

        assertThat(map.give(steve, LINK)).isTrue();

        MapView view = viewOf(steve.getInventory().getItemInMainHand());
        assertThat(view.getRenderers()).containsExactly(map);
        MapServer.Canvas canvas = new MapServer.Canvas();
        map.render(view, canvas, steve);
        assertThat(canvas.images).hasSize(1);
        assertThat(read((BufferedImage) canvas.images.getFirst())).isEqualTo(LINK);
        map.render(view, canvas, server.addPlayer("Alex"));
        assertThat(canvas.images).hasSize(1);
    }

    @Test
    @DisplayName("a full hand stays: the map goes to the first empty hotbar slot, which is selected")
    void aFullHandStays() {
        QrMap map = map();
        PlayerInventory inventory = steve.getInventory();
        inventory.setHeldItemSlot(0);
        inventory.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inventory.setItem(1, new ItemStack(Material.BREAD));

        assertThat(map.give(steve, LINK)).isTrue();

        assertThat(inventory.getItem(0).getType()).isEqualTo(Material.DIAMOND_SWORD);
        assertThat(inventory.getItem(1).getType()).isEqualTo(Material.BREAD);
        assertThat(inventory.getHeldItemSlot()).isEqualTo(2);
        assertThat(map.holds(inventory.getItemInMainHand())).isTrue();
    }

    @Test
    @DisplayName("a hotbar with no empty slot gets no map, and nothing in it moves")
    void aFullHotbarGetsNoMap() {
        QrMap map = map();
        PlayerInventory inventory = steve.getInventory();
        for (int slot = 0; slot < 9; slot++) {
            inventory.setItem(slot, new ItemStack(Material.STONE, slot + 1));
        }

        assertThat(map.give(steve, LINK)).isFalse();

        for (int slot = 0; slot < 9; slot++) {
            assertThat(inventory.getItem(slot)).isEqualTo(new ItemStack(Material.STONE, slot + 1));
        }
        assertThat(copies(map)).isZero();
    }

    @Test
    @DisplayName("a second setup reuses the map the player has, wherever it is, and draws the new link")
    void aSecondSetupReusesTheMap() throws Exception {
        QrMap map = map();
        PlayerInventory inventory = steve.getInventory();
        map.give(steve, LINK);
        inventory.setHeldItemSlot(5);
        inventory.setItem(5, new ItemStack(Material.BREAD));

        assertThat(map.give(steve, LINK + "&digits=6")).isTrue();

        assertThat(copies(map)).isOne();
        assertThat(inventory.getHeldItemSlot()).isZero();
        MapServer.Canvas canvas = new MapServer.Canvas();
        map.render(viewOf(inventory.getItemInMainHand()), canvas, steve);
        assertThat(read((BufferedImage) canvas.images.getFirst())).isEqualTo(LINK + "&digits=6");
    }

    @Test
    @DisplayName("taking it back removes every copy, in the off hand and on the cursor too, and leaves the rest")
    void takingItBackRemovesEveryCopy() {
        QrMap map = map();
        PlayerInventory inventory = steve.getInventory();
        map.give(steve, LINK);
        ItemStack copy = inventory.getItemInMainHand().clone();
        inventory.setItemInOffHand(copy.clone());
        inventory.setItem(20, copy.clone());
        steve.setItemOnCursor(copy.clone());
        inventory.setItem(21, new ItemStack(Material.BREAD));

        map.take(steve);

        assertThat(copies(map)).isZero();
        assertThat(map.holds(inventory.getItemInOffHand())).isFalse();
        assertThat(steve.getItemOnCursor().getType()).isEqualTo(Material.AIR);
        assertThat(inventory.getItem(21).getType()).isEqualTo(Material.BREAD);
        MapServer.Canvas canvas = new MapServer.Canvas();
        map.render(viewOf(copy), canvas, steve);
        assertThat(canvas.images).isEmpty();
    }

    @Test
    @DisplayName("a link too long for a map gets no map, and the player keeps the key and the link in chat")
    void aLinkTooLongGetsNoMap() {
        QrMap map = map();

        assertThat(map.give(steve, "otpauth://" + "x".repeat(1200))).isFalse();

        assertThat(copies(map)).isZero();
    }

    @Test
    @DisplayName("the server keeps one QR map across starts, apart from the captcha's")
    void oneMapAcrossStarts() throws Exception {
        map().give(steve, LINK);
        int id = viewOf(steve.getInventory().getItemInMainHand()).getId();

        assertThat(Files.readString(folder.resolve("qr-map.id")).strip()).isEqualTo(String.valueOf(id));
        QrMap second = map();
        assertThat(second.holds(steve.getInventory().getItemInMainHand())).isTrue();
        assertThat(KeptMap.holds(
                        new ItemStack(Material.BREAD),
                        viewOf(steve.getInventory().getItemInMainHand())))
                .isFalse();
    }
}
