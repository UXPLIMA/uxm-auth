package com.uxplima.auth.paper.adapter.outbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.paper.testing.DirectScheduler;
import com.uxplima.auth.paper.testing.QueuedScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/** What the hold takes from a waiting player, and how it comes back: at login, at quit, and after a crash. */
final class HoldKeeperTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private PluginMock plugin;
    private HeldStateStore store;
    private final Set<java.util.UUID> held = ConcurrentHashMap.newKeySet();
    private HoldKeeper keeper;
    private final List<Boolean> moves = new ArrayList<>();
    private final List<Runnable> later = new ArrayList<>();
    private boolean slowMoves;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        store = new HeldStateStore(folder);
        keeper = keeper(store);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("an operator is not one while held, and is one again after the login")
    void opIsTakenAndGivenBack() {
        PlayerMock steve = joining("Steve");
        steve.setOp(true);
        steve.setAllowFlight(true);

        keeper.arrive(steve);

        assertThat(steve.isOp()).isFalse();
        assertThat(steve.getAllowFlight()).isFalse();
        assertThat(store.load(steve.getUniqueId())).isPresent();

        held.remove(steve.getUniqueId());
        keeper.giveBack(steve);

        assertThat(steve.isOp()).isTrue();
        assertThat(steve.getAllowFlight()).isTrue();
        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("the inventory is hidden while held and every item comes back")
    void inventoryIsHiddenAndGivenBack() {
        PlayerMock steve = joining("Steve");
        steve.getInventory().setItem(4, new ItemStack(Material.DIAMOND, 7));

        keeper.arrive(steve);

        assertThat(steve.getInventory().isEmpty()).isTrue();

        held.remove(steve.getUniqueId());
        keeper.giveBack(steve);

        assertThat(steve.getInventory().getItem(4)).isEqualTo(new ItemStack(Material.DIAMOND, 7));
    }

    @Test
    @DisplayName("a held player is blind and unseen, and sees and is seen again after the login")
    void blindAndHiddenWhileHeld() {
        PlayerMock alex = server.addPlayer("Alex");
        PlayerMock steve = joining("Steve");

        keeper.arrive(steve);

        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
        assertThat(alex.canSee(steve)).isFalse();
        assertThat(steve.canSee(alex)).isFalse();

        held.remove(steve.getUniqueId());
        keeper.giveBack(steve);

        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isFalse();
        assertThat(alex.canSee(steve)).isTrue();
        assertThat(steve.canSee(alex)).isTrue();
    }

    @Test
    @DisplayName("a player who joins while another waits does not see the one who waits")
    void aLaterJoinerDoesNotSeeTheWaiting() {
        PlayerMock steve = joining("Steve");
        keeper.arrive(steve);

        PlayerMock alex = server.addPlayer("Alex");
        keeper.arrive(alex);

        assertThat(alex.canSee(steve)).isFalse();
        assertThat(steve.canSee(alex)).isFalse();
    }

    @Test
    @DisplayName("a state a crash left behind is given back, and the file is gone once the player is in")
    void aCrashGivesStateBack() {
        PlayerMock steve = joining("Steve");
        store.save(
                steve.getUniqueId(),
                new HeldState(true, true, false, 0.2f, 0.1f, 20.0, 20, GameMode.SURVIVAL, List.of()));
        HoldKeeper afterTheCrash = keeper(new HeldStateStore(folder));

        afterTheCrash.arrive(steve);
        held.remove(steve.getUniqueId());
        afterTheCrash.giveBack(steve);

        assertThat(steve.isOp()).isTrue();
        assertThat(steve.getAllowFlight()).isTrue();
        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("a player who is let straight in still gets back what a crash left behind")
    void aCrashStateReachesAPlayerWhoIsNotHeld() {
        PlayerMock steve = server.addPlayer("Steve");
        store.save(
                steve.getUniqueId(),
                new HeldState(true, false, false, 0.2f, 0.1f, 20.0, 20, GameMode.SURVIVAL, List.of()));

        keeper.arrive(steve);

        assertThat(steve.isOp()).isTrue();
        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("a held player who leaves is given everything back before the server saves them")
    void aQuitGivesBack() {
        PlayerMock steve = joining("Steve");
        steve.setOp(true);
        steve.getInventory().setItem(0, new ItemStack(Material.STONE));
        keeper.arrive(steve);

        keeper.leave(steve);

        assertThat(steve.isOp()).isTrue();
        assertThat(steve.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.STONE));
        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("with the stats switch off, an operator stays one while held")
    void eachTakeIsItsOwnSwitch() {
        keeper = new HoldKeeper(
                plugin,
                new DirectScheduler(),
                store,
                () -> new AuthSettings.Hold(
                        true, true, true, List.of(), true, true, true, true, true, true, true, false, false, false,
                        false),
                player -> held.contains(player.getUniqueId()));
        PlayerMock steve = joining("Steve");
        steve.setOp(true);
        steve.getInventory().setItem(0, new ItemStack(Material.STONE));

        keeper.arrive(steve);

        assertThat(steve.isOp()).isTrue();
        assertThat(steve.getInventory().getItem(0)).isEqualTo(new ItemStack(Material.STONE));
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isFalse();
    }

    @Test
    @DisplayName("a login before the write leaves no file, whichever disk task the server runs first")
    void aQuickLoginLeavesNoFile() {
        QueuedScheduler queued = new QueuedScheduler();
        HoldKeeper ordered = new HoldKeeper(
                plugin,
                queued,
                store,
                () -> new AuthSettings.Hold(
                        true, true, true, List.of(), true, true, true, true, true, true, true, true, true, true, true),
                player -> held.contains(player.getUniqueId()));
        PlayerMock steve = joining("Steve");
        steve.setOp(true);

        ordered.arrive(steve);
        queued.runOldest();
        held.remove(steve.getUniqueId());
        ordered.giveBack(steve);
        queued.runNewestFirst();

        assertThat(store.load(steve.getUniqueId())).isEmpty();
        assertThat(steve.isOp()).isTrue();
    }

    @Test
    @DisplayName("a player who left before the hold was set up leaves no file behind")
    void aPlayerWhoLeftFirstLeavesNoFile() {
        QueuedScheduler queued = new QueuedScheduler();
        HoldKeeper ordered = new HoldKeeper(
                plugin,
                queued,
                store,
                () -> new AuthSettings.Hold(
                        true, true, true, List.of(), true, true, true, true, true, true, true, true, true, true, true),
                player -> held.contains(player.getUniqueId()));
        PlayerMock steve = joining("Steve");

        ordered.arrive(steve);
        ordered.leave(steve);
        steve.disconnect();
        queued.runOldestFirst();

        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("with a login world, a waiting player is moved there and back to where they stood after the login")
    void aLoginWorldTakesThePlayerAndBringsThemBack() {
        World room = server.addSimpleWorld("uxmauth_login");
        keeper = keeper(store, Optional.of(new Location(room, 0.5, 65, 0.5)));
        PlayerMock steve = joining("Steve");
        Location stood = new Location(steve.getWorld(), 10.5, 70, -3.5, 90, 0);
        steve.teleport(stood);

        keeper.arrive(steve);
        assertThat(steve.getWorld()).isEqualTo(room);
        assertThat(store.load(steve.getUniqueId()).orElseThrow().place()).isEqualTo(HeldPlace.of(stood));

        held.remove(steve.getUniqueId());
        keeper.giveBack(steve);

        assertThat(steve.getLocation()).isEqualTo(stood);
    }

    @Test
    @DisplayName("a player who leaves from the login world keeps the file, and the next join brings them back")
    void aQuitFromTheLoginWorldIsBroughtBackAtTheNextJoin() {
        World room = server.addSimpleWorld("uxmauth_login");
        keeper = keeper(store, Optional.of(new Location(room, 0.5, 65, 0.5)));
        PlayerMock steve = joining("Steve");
        Location stood = new Location(steve.getWorld(), 10.5, 70, -3.5);
        steve.teleport(stood);
        keeper.arrive(steve);

        keeper.leave(steve);
        assertThat(store.load(steve.getUniqueId())).isPresent();

        held.remove(steve.getUniqueId());
        keeper.arrive(steve);

        assertThat(steve.getLocation()).isEqualTo(stood);
        assertThat(store.load(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("the plugin's own move to the login world is let through the hold that stops every other teleport")
    void theMoveIsMarkedAsTheKeepersOwn() {
        World room = server.addSimpleWorld("uxmauth_login");
        Passes passes = new Passes();
        keeper = keeper(store, Optional.of(new Location(room, 0.5, 65, 0.5)), passes);
        PlayerMock steve = joining("Steve");

        keeper.arrive(steve);

        assertThat(moves).containsExactly(true);
        assertThat(passes.isPassing(steve.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName(
            "a crash in the login world, then a join that waits again, still brings the player back to their place")
    void aCrashInTheLoginWorldKeepsThePlace() {
        World room = server.addSimpleWorld("uxmauth_login");
        keeper = keeper(store, Optional.of(new Location(room, 0.5, 65, 0.5)));
        PlayerMock steve = joining("Steve");
        Location stood = new Location(steve.getWorld(), 10.5, 70, -3.5);
        store.save(
                steve.getUniqueId(),
                new HeldState(
                        false, false, false, 0.2f, 0.1f, 20.0, 20, GameMode.SURVIVAL, List.of(), HeldPlace.of(stood)));
        // The server saved them where the hold had put them.
        steve.teleport(new Location(room, 0.5, 65, 0.5));
        slowMoves = true;

        keeper.arrive(steve);
        later.forEach(Runnable::run);
        slowMoves = false;
        assertThat(steve.getWorld()).isEqualTo(room);

        held.remove(steve.getUniqueId());
        keeper.giveBack(steve);

        assertThat(steve.getLocation()).isEqualTo(stood);
    }

    private PlayerMock joining(String name) {
        PlayerMock player = server.addPlayer(name);
        held.add(player.getUniqueId());
        return player;
    }

    private HoldKeeper keeper(HeldStateStore files, Optional<Location> room) {
        return keeper(files, room, new Passes());
    }

    private HoldKeeper keeper(HeldStateStore files, Optional<Location> room, Passes passes) {
        return new HoldKeeper(
                plugin,
                new DirectScheduler(),
                files,
                () -> new AuthSettings.Hold(
                        true, true, true, List.of(), true, true, true, true, true, true, true, true, true, true, true),
                player -> held.contains(player.getUniqueId()),
                () -> room,
                passes,
                (player, target) -> {
                    // MockBukkit has no teleportAsync; the move is made here, and whether it was marked is kept.
                    moves.add(passes.isPassing(player.getUniqueId()));
                    if (slowMoves) {
                        // A real teleport finishes later; until then the player still stands where they were.
                        later.add(() -> player.teleport(target));
                        return CompletableFuture.completedFuture(true);
                    }
                    return CompletableFuture.completedFuture(player.teleport(target));
                });
    }

    private HoldKeeper keeper(HeldStateStore files) {
        return new HoldKeeper(
                plugin,
                new DirectScheduler(),
                files,
                () -> new AuthSettings.Hold(
                        true, true, true, List.of(), true, true, true, true, true, true, true, true, true, true, true),
                player -> held.contains(player.getUniqueId()));
    }
}
