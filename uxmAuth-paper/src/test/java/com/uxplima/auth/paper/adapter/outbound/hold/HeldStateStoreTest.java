package com.uxplima.auth.paper.adapter.outbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/** The file that gives a player back what the hold took, even after a crash. */
final class HeldStateStoreTest {

    @TempDir
    Path folder;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("what is written is what is read back, the inventory included")
    void roundTrip() {
        HeldStateStore store = new HeldStateStore(folder);
        UUID player = UUID.randomUUID();
        HeldState state = new HeldState(
                true,
                true,
                false,
                0.2f,
                0.1f,
                17.5,
                12,
                GameMode.SURVIVAL,
                List.of(
                        new ItemStack(Material.DIAMOND, 3),
                        new ItemStack(Material.AIR),
                        new ItemStack(Material.STONE)));

        store.save(player, state);

        HeldState read = store.load(player).orElseThrow();
        assertThat(read.op()).isTrue();
        assertThat(read.allowFlight()).isTrue();
        assertThat(read.walkSpeed()).isEqualTo(0.2f);
        assertThat(read.health()).isEqualTo(17.5);
        assertThat(read.food()).isEqualTo(12);
        assertThat(read.gameMode()).isEqualTo(GameMode.SURVIVAL);
        assertThat(read.inventory()).hasSize(3);
        assertThat(read.inventory().get(0)).isEqualTo(new ItemStack(Material.DIAMOND, 3));
        assertThat(read.inventory().get(1).isEmpty()).isTrue();
        assertThat(read.inventory().get(2).getType()).isEqualTo(Material.STONE);
    }

    @Test
    @DisplayName("a player with nothing held has no file and reads nothing")
    void nothingHeld() {
        assertThat(new HeldStateStore(folder).load(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("deleting the file after a restore leaves nothing to restore twice")
    void deleteRemovesTheFile() {
        HeldStateStore store = new HeldStateStore(folder);
        UUID player = UUID.randomUUID();
        store.save(player, HeldState.empty(GameMode.ADVENTURE));

        store.delete(player);

        assertThat(store.load(player)).isEmpty();
        assertThat(folder.resolve(player + ".conf")).doesNotExist();
    }

    @Test
    @DisplayName("a file a crash cut short is kept for an operator, not read as an empty state")
    void aBrokenFileIsKept() throws IOException {
        HeldStateStore store = new HeldStateStore(folder);
        UUID player = UUID.randomUUID();
        Files.createDirectories(folder);
        Files.writeString(folder.resolve(player + ".conf"), "op = tru", StandardCharsets.UTF_8);

        assertThat(store.load(player)).isEmpty();
        assertThat(folder.resolve(player + ".conf.broken")).exists();
    }

    @Test
    @DisplayName("a write lands whole or not at all, so a crash mid-write never leaves half a file")
    void writesAreWhole() throws IOException {
        HeldStateStore store = new HeldStateStore(folder);
        UUID player = UUID.randomUUID();
        store.save(player, HeldState.empty(GameMode.SURVIVAL));

        try (var files = Files.list(folder)) {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly(player + ".conf");
        }
        assertThat(Arrays.asList(folder.toFile().list())).doesNotContain(player + ".conf.tmp");
    }
}
