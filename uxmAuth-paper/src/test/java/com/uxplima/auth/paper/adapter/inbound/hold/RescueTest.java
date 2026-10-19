package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.adapter.outbound.hold.Passes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A player who joins inside a wall or a portal is moved to the nearest safe spot above. */
final class RescueTest {

    private ServerMock server;
    private World world;
    private final List<Location> moved = new ArrayList<>();
    private final Passes passes = new Passes();
    private boolean on = true;
    private Rescue rescue;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("w");
        for (int y = 60; y < 80; y++) {
            world.getBlockAt(0, y, 0).setType(Material.AIR);
        }
        rescue = new Rescue(() -> new AuthSettings.Spots(Optional.empty(), on), passes, (player, target) -> {
            moved.add(target);
            assertThat(passes.isPassing(player.getUniqueId()))
                    .as("the hold lets the move through")
                    .isTrue();
            return CompletableFuture.completedFuture(true);
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private PlayerMock standingAt(double y) {
        PlayerMock player = server.addPlayer();
        player.setLocation(new Location(world, 0.5, y, 0.5, 45, 5));
        return player;
    }

    @Test
    @DisplayName("inside stone, the player goes up to the first spot with room for them and ground under them")
    void outOfAWall() {
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        world.getBlockAt(0, 65, 0).setType(Material.STONE);
        PlayerMock player = standingAt(64);

        rescue.check(player);

        assertThat(moved).singleElement().satisfies(target -> {
            assertThat(target.getY()).isEqualTo(66.0);
            assertThat(target.getX()).isEqualTo(0.5);
            assertThat(target.getYaw()).isEqualTo(45f);
        });
        assertThat(passes.isPassing(player.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("in a portal, the player is moved out of it")
    void outOfAPortal() {
        world.getBlockAt(0, 63, 0).setType(Material.OBSIDIAN);
        world.getBlockAt(0, 64, 0).setType(Material.NETHER_PORTAL);
        world.getBlockAt(0, 65, 0).setType(Material.NETHER_PORTAL);
        world.getBlockAt(0, 66, 0).setType(Material.OBSIDIAN);
        PlayerMock player = standingAt(64);

        rescue.check(player);

        assertThat(moved)
                .singleElement()
                .satisfies(target -> assertThat(target.getY()).isEqualTo(67.0));
    }

    @Test
    @DisplayName("with no floor anywhere above, the player goes to the world's spawn rather than over the portal")
    void noFloorMeansTheSpawn() {
        world.getBlockAt(0, 63, 0).setType(Material.OBSIDIAN);
        world.getBlockAt(0, 64, 0).setType(Material.NETHER_PORTAL);
        world.setSpawnLocation(10, 70, 10);
        PlayerMock player = standingAt(64);

        rescue.check(player);

        assertThat(moved).singleElement().satisfies(target -> {
            assertThat(target.getBlockX()).isEqualTo(10);
            assertThat(target.getBlockY()).isEqualTo(70);
            assertThat(target.getBlockZ()).isEqualTo(10);
        });
    }

    @Test
    @DisplayName("on open ground, on a slab, a carpet or in a doorway, or with the rescue off, nobody is moved")
    void standingFreelyStays() {
        world.getBlockAt(0, 63, 0).setType(Material.STONE);
        rescue.check(standingAt(64));
        world.getBlockAt(0, 64, 0).setType(Material.STONE_SLAB);
        rescue.check(standingAt(64.5));
        world.getBlockAt(0, 64, 0).setType(Material.WHITE_CARPET);
        rescue.check(standingAt(64.0625));

        world.getBlockAt(0, 64, 0).setType(Material.OAK_DOOR);
        world.getBlockAt(0, 65, 0).setType(Material.OAK_DOOR);
        rescue.check(standingAt(64));
        world.getBlockAt(0, 65, 0).setType(Material.AIR);

        on = false;
        world.getBlockAt(0, 64, 0).setType(Material.STONE);
        rescue.check(standingAt(64));

        assertThat(moved).isEmpty();
    }
}
