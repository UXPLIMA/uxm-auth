package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;

import io.papermc.paper.event.player.AsyncChatEvent;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.plugin.PluginMock;

/** What a player who has not logged in may not do, one switch at a time. */
final class HoldListenerTest {

    private ServerMock server;
    private PluginMock plugin;
    private Sessions sessions;
    private AtomicReference<AuthSettings.Hold> hold;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sessions = new Sessions();
        hold = new AtomicReference<>(holding(true));
        server.getPluginManager()
                .registerEvents(
                        new HoldListener(sessions, hold::get, () -> Set.of("login", "l", "register"), "uxmauth"),
                        plugin);
        steve = server.addPlayer("Steve");
        sessions.open(steve.getUniqueId(), new AuthState.Registered(account(steve), Attempts.fresh(3)));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a held player cannot walk, and can still turn their head")
    void aHeldPlayerCannotWalkButCanTurn() {
        Location from = steve.getLocation();
        Location walked = from.clone().add(1, 0, 0);
        Location turned = from.clone();
        turned.setYaw(from.getYaw() + 90);

        assertThat(fire(new PlayerMoveEvent(steve, from, walked)).isCancelled()).isTrue();
        assertThat(fire(new PlayerMoveEvent(steve, from, turned)).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a held player's chat goes nowhere")
    void chatIsHeld() {
        ChatWatcher watcher = new ChatWatcher();
        server.getPluginManager().registerEvents(watcher, plugin);

        steve.chat("my password is hunter22");
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(watcher.cancelled).isTrue();
    }

    @Test
    @DisplayName("this plugin's own commands pass, by any label and with the namespace")
    void anAllowedCommandPasses() {
        assertThat(command("/login hunter22").isCancelled()).isFalse();
        assertThat(command("/L hunter22").isCancelled()).isFalse();
        assertThat(command("/uxmauth:login hunter22").isCancelled()).isFalse();
    }

    @Test
    @DisplayName("any other command is held, unless the operator allowed it")
    void anotherCommandIsHeld() {
        assertThat(command("/spawn").isCancelled()).isTrue();

        hold.set(new AuthSettings.Hold(
                true, true, true, List.of("spawn"), true, true, true, true, true, true, true, true, true, true, true));

        assertThat(command("/spawn").isCancelled()).isFalse();
        assertThat(command("/spawnmob").isCancelled()).isTrue();
    }

    @Test
    @DisplayName("each hold is its own switch")
    void eachHoldCanBeSwitchedOff() {
        hold.set(holding(false));
        Location from = steve.getLocation();

        assertThat(fire(new PlayerMoveEvent(steve, from, from.clone().add(1, 0, 0)))
                        .isCancelled())
                .isFalse();
        assertThat(command("/spawn").isCancelled()).isFalse();
        assertThat(fire(new BlockBreakEvent(block(), steve)).isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a held player breaks nothing and is teleported by nothing")
    void blocksAndTeleportsAreHeld() {
        Location from = steve.getLocation();

        assertThat(fire(new BlockBreakEvent(block(), steve)).isCancelled()).isTrue();
        assertThat(fire(new PlayerTeleportEvent(steve, from, from.clone().add(50, 0, 0)))
                        .isCancelled())
                .isTrue();
    }

    @Test
    @DisplayName("a logged-in player is not held at all")
    void anAuthenticatedPlayerIsFree() {
        sessions.open(steve.getUniqueId(), new AuthState.Authenticated(account(steve), AuthMethod.PASSWORD));
        Location from = steve.getLocation();

        assertThat(fire(new PlayerMoveEvent(steve, from, from.clone().add(1, 0, 0)))
                        .isCancelled())
                .isFalse();
        assertThat(command("/spawn").isCancelled()).isFalse();
    }

    @Test
    @DisplayName("a real player this plugin holds no session for is held: a missing session fails closed")
    void aPlayerWithoutASessionIsHeld() {
        PlayerMock stranger = server.addPlayer("Stranger");
        Location from = stranger.getLocation();

        assertThat(fire(new PlayerMoveEvent(stranger, from, from.clone().add(1, 0, 0)))
                        .isCancelled())
                .isTrue();
    }

    @Test
    @DisplayName("an NPC another plugin marks as one is never held")
    @SuppressWarnings("deprecation") // Citizens marks an NPC with metadata, which Paper deprecated.
    void anNpcIsNeverHeld() {
        PlayerMock npc = server.addPlayer("Guard");
        npc.setMetadata("NPC", new FixedMetadataValue(plugin, true));
        Location from = npc.getLocation();

        assertThat(fire(new PlayerMoveEvent(npc, from, from.clone().add(1, 0, 0)))
                        .isCancelled())
                .isFalse();
    }

    @Test
    @DisplayName("another plugin's command under one of this plugin's labels is held")
    void aNamespacedCommandOfAnotherPluginIsHeld() {
        assertThat(command("/evil:login hunter22").isCancelled()).isTrue();
        assertThat(command("/uxmauth:login hunter22").isCancelled()).isFalse();
    }

    @Test
    @DisplayName("an allowed command is compared whole, so a namespace passes only when it is written")
    void allowedCommandsAreComparedWhole() {
        hold.set(new AuthSettings.Hold(
                true, true, true, List.of("spawn"), true, true, true, true, true, true, true, true, true, true, true));
        assertThat(command("/essentials:spawn").isCancelled()).isTrue();

        hold.set(new AuthSettings.Hold(
                true,
                true,
                true,
                List.of("essentials:spawn"),
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true));
        assertThat(command("/essentials:spawn").isCancelled()).isFalse();
    }

    @Test
    @DisplayName("the legacy chat event is held too, so no old chat listener reads a typed password")
    @SuppressWarnings("deprecation")
    void legacyChatIsHeld() {
        org.bukkit.event.player.AsyncPlayerChatEvent legacy =
                new org.bukkit.event.player.AsyncPlayerChatEvent(true, steve, "hunter22", Set.of());

        java.util.concurrent.Future<?> fired = server.getScheduler().executeAsyncEvent(legacy);
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(fired).isDone();
        assertThat(legacy.isCancelled()).isTrue();
    }

    private PlayerCommandPreprocessEvent command(String line) {
        return fire(new PlayerCommandPreprocessEvent(steve, line));
    }

    private <T extends Event & Cancellable> T fire(T event) {
        server.getPluginManager().callEvent(event);
        return event;
    }

    private Block block() {
        Block block = steve.getWorld().getBlockAt(0, 64, 0);
        block.setType(Material.STONE);
        return block;
    }

    private static AuthSettings.Hold holding(boolean on) {
        return new AuthSettings.Hold(on, on, on, List.of(), on, on, on, on, on, on, on, on, on, on, on);
    }

    private static Account account(PlayerMock player) {
        return new Account(
                player.getUniqueId(),
                new PlayerName(player.getName()),
                AccountKind.CRACKED,
                null,
                new PasswordHash("$2b$04$abcdefghijklmnopqrstuu5Lo0g67CiD3M4RpN1BmBb4Crp5w7dbK"),
                Instant.EPOCH,
                "127.0.0.1",
                null,
                null);
    }

    /** Sees the chat after every other listener, to read whether it was let through. */
    public static final class ChatWatcher implements Listener {

        volatile @Nullable Boolean cancelled;

        @EventHandler(priority = EventPriority.MONITOR)
        public void seen(AsyncChatEvent event) {
            cancelled = event.isCancelled();
        }
    }
}
