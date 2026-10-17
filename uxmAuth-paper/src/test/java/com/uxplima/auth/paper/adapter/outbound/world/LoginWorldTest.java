package com.uxplima.auth.paper.adapter.outbound.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** The empty world a waiting player stands in, made once, and nothing at all outside that waiting room. */
final class LoginWorldTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private ScreenSettings settings;
    private final List<String> made = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        settings = room(ScreenSettings.WaitingRoom.LOGIN_WORLD, "lobby_wait");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the world the file names is made once, with a block to stand on under the place players wait")
    void theWorldIsMadeWithABlockToStandOn() {
        LoginWorld world = world();

        world.prepare();
        world.prepare();

        assertThat(made).containsExactly("lobby_wait");
        Location room = world.room().orElseThrow();
        assertThat(room.getWorld()).isEqualTo(server.getWorld("lobby_wait"));
        assertThat(room.clone().subtract(0, 1, 0).getBlock().getType()).isEqualTo(Material.BARRIER);
    }

    @Test
    @DisplayName("a world that already has the name is used as it is")
    void anExistingWorldIsUsed() {
        World existing = server.addSimpleWorld("lobby_wait");
        LoginWorld world = world();

        world.prepare();

        assertThat(made).isEmpty();
        assertThat(world.room().orElseThrow().getWorld()).isEqualTo(existing);
    }

    @Test
    @DisplayName("outside the login-world waiting room nothing is made and nobody is moved")
    void otherWaitingRoomsMakeNothing() {
        settings = room(ScreenSettings.WaitingRoom.FROZEN, "lobby_wait");
        LoginWorld world = world();

        world.prepare();

        assertThat(made).isEmpty();
        assertThat(world.room()).isEmpty();
        server.addSimpleWorld("lobby_wait");
        assertThat(world.room()).isEmpty();
    }

    @Test
    @DisplayName("a server that cannot make a world, as Folia, leaves the players frozen where they are")
    void aServerThatCannotMakeAWorldFreezes() {
        LoginWorld world = new LoginWorld(server, () -> settings, Logger.getLogger("uxmauth-test"), creator -> {
            throw new UnsupportedOperationException("Folia");
        });

        world.prepare();

        assertThat(world.room()).isEmpty();
    }

    private LoginWorld world() {
        return new LoginWorld(server, () -> settings, Logger.getLogger("uxmauth-test"), (WorldCreator creator) -> {
            made.add(creator.name());
            return server.addSimpleWorld(creator.name());
        });
    }

    private ScreenSettings room(ScreenSettings.WaitingRoom waitingRoom, String name) {
        ScreenSettings shipped = new ScreenFile(folder.resolve("screens.conf")).current();
        return new ScreenSettings(
                waitingRoom,
                shipped.cancel(),
                shipped.canClose(),
                shipped.pinPad(),
                shipped.rules(),
                shipped.register(),
                shipped.login(),
                shipped.pin(),
                name);
    }
}
