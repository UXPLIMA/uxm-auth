package com.uxplima.auth.paper.adapter.outbound.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;

import com.uxplima.auth.adapter.outbound.CaptchaImage;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.domain.protection.CaptchaPicture;
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

/** The code on a map in the hand: one map for the server, each player drawn the picture of their own code. */
final class CaptchaMapTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private PlayerMock steve;
    private Captchas.JavaScreen screen = Captchas.JavaScreen.MAP;
    private boolean hidesInventory = true;
    private final Set<UUID> bedrock = new HashSet<>();

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

    private org.bukkit.@Nullable Server makes;

    private CaptchaMap map() {
        if (makes == null) {
            makes = new MapServer(server).server();
        }
        return CaptchaMap.open(
                        java.util.Objects.requireNonNull(makes),
                        folder.resolve("captcha-map.id"),
                        () -> new Captchas.Settings(
                                Captchas.When.REGISTER, 3, 5, "ABC", Duration.ofMinutes(60), screen),
                        bedrock::contains,
                        () -> hidesInventory,
                        new SplittableRandom(9),
                        Logger.getLogger("captcha-test"))
                .orElseThrow();
    }

    private static MapView viewOf(ItemStack item) {
        assertThat(item.getType()).isEqualTo(Material.FILLED_MAP);
        return java.util.Objects.requireNonNull(((MapMeta) item.getItemMeta()).getMapView());
    }

    @Test
    @DisplayName("the map goes in the hand of a waiting Java player, and its renderer draws them their code")
    void theMapGoesInTheHand() {
        CaptchaMap map = map();

        assertThat(map.give(steve, "K7QXR")).isTrue();

        MapView view = viewOf(steve.getInventory().getItemInMainHand());
        assertThat(view.getRenderers()).containsExactly(map);
        assertThat(view.isTrackingPosition()).isFalse();
        assertThat(view.isLocked()).isTrue();
        MapServer.Canvas canvas = new MapServer.Canvas();
        map.render(view, canvas, steve);
        assertThat(canvas.images).hasSize(1);
        BufferedImage drawn = (BufferedImage) canvas.images.getFirst();
        CaptchaPicture expected = CaptchaPicture.draw("K7QXR", new SplittableRandom(9));
        for (int y = 0; y < CaptchaPicture.SIZE; y += 7) {
            for (int x = 0; x < CaptchaPicture.SIZE; x += 3) {
                assertThat(drawn.getRGB(x, y))
                        .isEqualTo((expected.ink(x, y) ? CaptchaImage.INK : CaptchaImage.PAPER).getRGB());
            }
        }
    }

    @Test
    @DisplayName("a picture is drawn once, a new code draws again, and another player is drawn their own")
    void eachPlayerIsDrawnTheirOwnOnce() {
        CaptchaMap map = map();
        PlayerMock alex = server.addPlayer("Alex");
        map.give(steve, "AAAAA");
        MapView view = viewOf(steve.getInventory().getItemInMainHand());
        MapServer.Canvas canvas = new MapServer.Canvas();

        map.render(view, canvas, steve);
        map.render(view, canvas, steve);
        map.render(view, canvas, alex);
        assertThat(canvas.images).hasSize(1);

        map.give(steve, "BBBBB");
        map.render(view, canvas, steve);
        map.give(alex, "CCCCC");
        map.render(view, canvas, alex);
        assertThat(canvas.images).hasSize(3);
        assertThat(pixels(canvas.images.get(1))).isNotEqualTo(pixels(canvas.images.get(2)));
    }

    @Test
    @DisplayName("a player who left is forgotten: nothing is drawn for them")
    void aPlayerWhoLeftIsForgotten() {
        CaptchaMap map = map();
        map.give(steve, "AAAAA");
        MapView view = viewOf(steve.getInventory().getItemInMainHand());

        map.forget(steve.getUniqueId());
        MapServer.Canvas canvas = new MapServer.Canvas();
        map.render(view, canvas, steve);

        assertThat(canvas.images).isEmpty();
    }

    @Test
    @DisplayName("no map for the dialog setting, a Bedrock player, or a hold that leaves the inventory in place")
    void noMapWhereItDoesNotBelong() {
        CaptchaMap map = map();
        screen = Captchas.JavaScreen.DIALOG;
        assertThat(map.give(steve, "AAAAA")).isFalse();

        screen = Captchas.JavaScreen.MAP;
        bedrock.add(steve.getUniqueId());
        assertThat(map.give(steve, "AAAAA")).isFalse();

        bedrock.clear();
        hidesInventory = false;
        assertThat(map.give(steve, "AAAAA")).isFalse();
        assertThat(steve.getInventory().getItemInMainHand().getType()).isEqualTo(Material.AIR);
    }

    @Test
    @DisplayName("an item in the hand is never replaced: the hold had not emptied it, so the code stays on the screen")
    void anItemInTheHandStays() {
        CaptchaMap map = map();
        steve.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));

        assertThat(map.give(steve, "AAAAA")).isFalse();

        assertThat(steve.getInventory().getItemInMainHand().getType()).isEqualTo(Material.DIAMOND_SWORD);
    }

    @Test
    @DisplayName("a map from another plugin in the hand is not ours to replace either")
    void anotherMapStays() {
        CaptchaMap map = map();
        ItemStack theirs = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) java.util.Objects.requireNonNull(theirs.getItemMeta());
        meta.setMapView(java.util.Objects.requireNonNull(makes)
                .createMap(server.getWorlds().getFirst()));
        theirs.setItemMeta(meta);
        steve.getInventory().setItemInMainHand(theirs);

        assertThat(map.give(steve, "AAAAA")).isFalse();
        assertThat(steve.getInventory().getItemInMainHand()).isEqualTo(theirs);
    }

    @Test
    @DisplayName("the server keeps one map: its number is written once and the next start takes the same map")
    void oneMapAcrossStarts() throws Exception {
        CaptchaMap first = map();
        first.give(steve, "AAAAA");
        int id = viewOf(steve.getInventory().getItemInMainHand()).getId();

        assertThat(Files.readString(folder.resolve("captcha-map.id")).strip()).isEqualTo(String.valueOf(id));
        CaptchaMap second = map();
        second.give(steve, "BBBBB");

        assertThat(viewOf(steve.getInventory().getItemInMainHand()).getId()).isEqualTo(id);
        assertThat(viewOf(steve.getInventory().getItemInMainHand()).getRenderers())
                .containsExactly(second);
    }

    private static int[] pixels(Image image) {
        BufferedImage drawn = (BufferedImage) image;
        return drawn.getRGB(0, 0, drawn.getWidth(), drawn.getHeight(), null, 0, drawn.getWidth());
    }
}
