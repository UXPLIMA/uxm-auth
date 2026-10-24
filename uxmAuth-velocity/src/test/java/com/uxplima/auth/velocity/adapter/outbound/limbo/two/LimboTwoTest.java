package com.uxplima.auth.velocity.adapter.outbound.limbo.two;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboKit;
import com.uxplima.auth.velocity.adapter.outbound.limbo.LimboKits;
import com.uxplima.auth.velocity.adapter.outbound.limbo.WorldFileKind;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.Limbo;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.LimboSessionHandler;
import net.elytrium.limboapi.api.world.BuiltInWorldFileType;
import net.elytrium.limboapi.api.world.VirtualWorld;
import net.elytrium.limboapi.api.world.WorldFile;
import net.elytrium.limboapi.api.world.chunk.Dimension;
import net.elytrium.limboapi.api.world.chunk.block.VirtualBlock;
import net.elytrium.limboapi.api.world.player.GameMode;
import net.elytrium.limboapi.api.world.player.LimboPlayer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.slf4j.helpers.NOPLogger;

/** The waiting world on LimboAPI 2: what it builds, what it registers, and what it hands a guest. */
final class LimboTwoTest {

    private final LimboFactory factory = mock(LimboFactory.class);
    private final VirtualWorld world = mock(VirtualWorld.class);
    private final Limbo limbo = mock(Limbo.class, RETURNS_SELF);
    private final VirtualBlock stone = mock(VirtualBlock.class);
    private final AuthSettings.Network settings = mock(AuthSettings.Network.class);

    LimboTwoTest() {
        when(factory.createVirtualWorld(Dimension.OVERWORLD, 0.5, 100, 0.5, 90f, 0f))
                .thenReturn(world);
        when(factory.createLimbo(world)).thenReturn(limbo);
        when(factory.createSimpleBlock("minecraft:stone")).thenReturn(stone);
        when(settings.limboSpawn()).thenReturn(new AuthSettings.Spot(0.5, 100, 0.5, 90f, 0f));
        when(settings.limboFile()).thenReturn("limbo.schem");
    }

    private LimboKit.Room opened(Path folder, List<CommandMeta> commands) {
        return new LimboTwo().open(factory, folder, settings, commands, NOPLogger.NOP_LOGGER);
    }

    @Test
    @DisplayName("LimboAPI 2 is answered by this kit")
    void theKitsFindThisOne() {
        assertThat(LimboKits.of(factory)).containsInstanceOf(LimboTwo.class);
    }

    @Test
    @DisplayName("with no world file the player stands on stone under the spawn, in adventure mode")
    void withNoFileAPlatform(@TempDir Path folder) {
        opened(folder, List.of());

        verify(world, times(25)).setBlock(anyInt(), eq(99), anyInt(), eq(stone));
        verify(limbo).setName("uxmAuth");
        verify(limbo).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    @DisplayName("a world file is pasted where it was saved, and no platform is laid")
    void aWorldFileIsPasted(@TempDir Path folder) throws IOException {
        Path file = Files.writeString(folder.resolve("limbo.schem"), "");
        WorldFile read = mock(WorldFile.class);
        when(factory.openWorldFile(BuiltInWorldFileType.WORLDEDIT_SCHEM, file)).thenReturn(read);

        opened(folder, List.of());

        verify(read).toWorld(factory, world, 0, 0, 0);
        verify(world, never()).setBlock(anyInt(), anyInt(), anyInt(), eq(stone));
    }

    @Test
    @DisplayName("every kind of world file has a type of this generation")
    void everyKindHasAType() {
        for (WorldFileKind kind : WorldFileKind.values()) {
            assertThatCode(() -> BuiltInWorldFileType.valueOf(kind.name())).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("every command the world answers is registered with the limbo")
    void theCommandsAreRegistered(@TempDir Path folder) {
        CommandMeta login = mock(CommandMeta.class);
        CommandMeta register = mock(CommandMeta.class);

        opened(folder, List.of(login, register));

        verify(limbo).registerCommand(login);
        verify(limbo).registerCommand(register);
    }

    @Test
    @DisplayName("what LimboAPI says of a player reaches the guest, and the guest's asks reach the player")
    void theGuestHearsThePlayer(@TempDir Path folder) {
        Player player = mock(Player.class);
        LimboKit.Guest guest = mock(LimboKit.Guest.class);
        opened(folder, List.of()).spawn(player, guest);
        ArgumentCaptor<LimboSessionHandler> handler = ArgumentCaptor.forClass(LimboSessionHandler.class);
        verify(limbo).spawnPlayer(eq(player), handler.capture());
        LimboPlayer inWorld = mock(LimboPlayer.class);
        ScheduledExecutorService loop = mock(ScheduledExecutorService.class);
        when(inWorld.getScheduledExecutor()).thenReturn(loop);

        handler.getValue().onSpawn(limbo, inWorld);
        handler.getValue().onChat("/login hunter22");
        handler.getValue().onDisconnect();

        ArgumentCaptor<LimboKit.Inside> inside = ArgumentCaptor.forClass(LimboKit.Inside.class);
        verify(guest).arrive(inside.capture());
        verify(guest).said("/login hunter22");
        verify(guest).left();
        BufferedImage picture = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        inside.getValue().stopFalling();
        inside.getValue().draw(picture);
        inside.getValue().leave();
        assertThat(inside.getValue().loop()).isSameAs(loop);
        verify(inWorld).disableFalling();
        verify(inWorld).sendImage(picture);
        verify(inWorld).disconnect();
    }
}
