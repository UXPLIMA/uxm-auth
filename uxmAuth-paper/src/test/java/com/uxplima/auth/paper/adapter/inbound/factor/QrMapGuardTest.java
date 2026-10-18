package com.uxplima.auth.paper.adapter.inbound.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockito.Mockito;

/** The QR map never outlives the setup: dropped it vanishes, it is no death drop, and a join or a quit takes it. */
final class QrMapGuardTest {

    private ServerMock server;
    private PlayerMock steve;
    private final List<String> taken = new ArrayList<>();
    private final List<String> left = new ArrayList<>();
    private QrMapGuard guard;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        steve = server.addPlayer("Steve");
        guard = new QrMapGuard(
                item -> item.getType() == Material.FILLED_MAP,
                player -> taken.add(player.getName()),
                player -> left.add(player.getName()));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a dropped QR map vanishes, and any other drop lands")
    void aDroppedMapVanishes() {
        Item dropped = Mockito.mock(Item.class);
        Mockito.when(dropped.getItemStack()).thenReturn(new ItemStack(Material.FILLED_MAP));
        PlayerDropItemEvent drop = new PlayerDropItemEvent(steve, dropped);

        guard.onDrop(drop);

        Mockito.verify(dropped).remove();
        Item bread = Mockito.mock(Item.class);
        Mockito.when(bread.getItemStack()).thenReturn(new ItemStack(Material.BREAD));
        guard.onDrop(new PlayerDropItemEvent(steve, bread));
        Mockito.verify(bread, Mockito.never()).remove();
    }

    @Test
    @DisplayName("a death drops everything but the QR map")
    void aDeathDropsNoMap() {
        List<ItemStack> drops =
                new ArrayList<>(List.of(new ItemStack(Material.FILLED_MAP), new ItemStack(Material.BREAD)));
        PlayerDeathEvent death = Mockito.mock(PlayerDeathEvent.class);
        Mockito.when(death.getDrops()).thenReturn(drops);

        guard.onDeath(death);

        assertThat(drops).containsExactly(new ItemStack(Material.BREAD));
    }

    @Test
    @DisplayName("a join takes back a map a crash left behind, and a quit ends the setup")
    void aJoinAndAQuitTakeIt() {
        guard.onJoin(new PlayerJoinEvent(steve, Component.empty()));
        guard.onQuit(new PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(taken).containsExactly("Steve");
        assertThat(left).containsExactly("Steve");
    }
}
