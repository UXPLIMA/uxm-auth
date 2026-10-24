package com.uxplima.auth.velocity.adapter.outbound.limbo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.velocity.adapter.inbound.LimboVisit;
import com.velocitypowered.api.proxy.Player;
import net.elytrium.limboapi.api.event.LoginLimboRegisterEvent;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** The glue between the waiting world and a visit: who is held, what the world does, and when the time runs out. */
final class LimboWaitTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final AuthSettings.Timeouts LIMITS =
            new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5));

    private final LimboKit.Room room = mock(LimboKit.Room.class);
    private final Player player = mock(Player.class);
    private final LimboKit.Inside inWorld = mock(LimboKit.Inside.class);
    private final ScheduledExecutorService loop = mock(ScheduledExecutorService.class);
    private final LimboVisit visit = mock(LimboVisit.class);
    private final ScheduledFuture<?> timer = mock(ScheduledFuture.class);
    private LimboVisit.@Nullable Exit exit;
    private boolean waits = true;

    LimboWaitTest() {
        when(player.getUniqueId()).thenReturn(STEVE);
        when(inWorld.loop()).thenReturn(loop);
        doReturn(timer).when(loop).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
        doReturn(timer).when(loop).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        when(visit.said(any())).thenReturn(CompletableFuture.completedFuture(null));
    }

    private LimboWait holding() {
        return new LimboWait(
                room,
                who -> waits ? Optional.of(new LimboWait.Waiter(STEVE, new PlayerName("Steve"))) : Optional.empty(),
                (who, waiter, made) -> {
                    assertThat(waiter.id()).isEqualTo(STEVE);
                    exit = made;
                    return visit;
                },
                () -> LIMITS,
                who -> Component.text("too long"),
                org.slf4j.helpers.NOPLogger.NOP_LOGGER);
    }

    /** The guest a held player is spawned with, once LimboAPI runs the join callback. */
    private LimboKit.Guest spawned() {
        LoginLimboRegisterEvent event = new LoginLimboRegisterEvent(player);
        holding().register(event);
        assertThat(event.getOnJoinCallbacks()).hasSize(1);
        event.getOnJoinCallbacks().poll().run();
        ArgumentCaptor<LimboKit.Guest> guest = ArgumentCaptor.forClass(LimboKit.Guest.class);
        verify(room).spawn(eq(player), guest.capture());
        return guest.getValue();
    }

    @Test
    @DisplayName("the world file is read by its extension, and a file of no known kind is not read")
    void theWorldFileIsReadByItsExtension() {
        assertThat(WorldFileKind.of("limbo.schem")).contains(WorldFileKind.WORLDEDIT_SCHEM);
        assertThat(WorldFileKind.of("LOBBY.SCHEM")).contains(WorldFileKind.WORLDEDIT_SCHEM);
        assertThat(WorldFileKind.of("old.schematic")).contains(WorldFileKind.SCHEMATIC);
        assertThat(WorldFileKind.of("room.nbt")).contains(WorldFileKind.STRUCTURE);
        assertThat(WorldFileKind.of("notes.txt")).isEmpty();
    }

    @Test
    @DisplayName("only a player waiting for the proxy's password is held in the world")
    void onlyAWaitingPlayerIsHeld() {
        waits = false;
        LoginLimboRegisterEvent event = new LoginLimboRegisterEvent(player);

        holding().register(event);

        assertThat(event.getOnJoinCallbacks()).isEmpty();
    }

    @Test
    @DisplayName(
            "a held player stops falling, is told what to type, and is sent away when the time to register runs out")
    void aHeldPlayerArrivesAndHasATimeLimit() {
        LimboKit.Guest guest = spawned();

        guest.arrive(inWorld);

        verify(inWorld).stopFalling();
        verify(visit).arrive();
        ArgumentCaptor<Runnable> timeUp = ArgumentCaptor.forClass(Runnable.class);
        verify(loop).schedule(timeUp.capture(), eq(90_000L), eq(TimeUnit.MILLISECONDS));
        timeUp.getValue().run();
        verify(visit).timeUp(Component.text("too long"));
        verify(loop).scheduleAtFixedRate(any(Runnable.class), eq(5_000L), eq(5_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("a player with an account has the time to log in")
    void aRegisteredPlayerHasTheLoginLimit() {
        when(visit.registered()).thenReturn(true);

        spawned().arrive(inWorld);

        verify(loop).schedule(any(Runnable.class), eq(60_000L), eq(TimeUnit.MILLISECONDS));
    }

    @Test
    @DisplayName("a line the player says reaches the visit, and leaving stops the clocks")
    void chatReachesTheVisitAndLeavingStopsTheClocks() {
        LimboKit.Guest guest = spawned();
        guest.arrive(inWorld);

        guest.said("/login hunter22");
        guest.left();

        verify(visit).said("/login hunter22");
        verify(visit).left();
        verify(timer, org.mockito.Mockito.times(2)).cancel(false);
    }

    @Test
    @DisplayName("a player let out leaves the world on its own thread, and one sent away is disconnected")
    void theExitActsOnTheWorld() {
        spawned().arrive(inWorld);
        LimboVisit.Exit made = java.util.Objects.requireNonNull(exit);

        made.release();
        verify(inWorld, never()).leave();
        ArgumentCaptor<Runnable> onLoop = ArgumentCaptor.forClass(Runnable.class);
        verify(loop).execute(onLoop.capture());
        onLoop.getValue().run();
        verify(inWorld).leave();

        made.sendAway(Component.text("bye"));
        verify(player).disconnect(Component.text("bye"));
        made.tell(Component.text("hello"));
        verify(player).sendMessage(Component.text("hello"));
    }

    @Test
    @DisplayName("a captcha goes on a map in the hand, drawn pixel for pixel")
    void theCaptchaGoesOnAMap() {
        spawned().arrive(inWorld);
        com.uxplima.auth.domain.protection.CaptchaPicture picture =
                com.uxplima.auth.domain.protection.CaptchaPicture.draw("K7QXR", new java.util.SplittableRandom(2));

        java.util.Objects.requireNonNull(exit).draw(picture);

        ArgumentCaptor<java.awt.image.BufferedImage> image =
                ArgumentCaptor.forClass(java.awt.image.BufferedImage.class);
        verify(inWorld).draw(image.capture());
        java.awt.image.BufferedImage expected = com.uxplima.auth.adapter.outbound.CaptchaImage.of(picture);
        assertThat(image.getValue().getRGB(0, 0, 128, 128, null, 0, 128))
                .isEqualTo(expected.getRGB(0, 0, 128, 128, null, 0, 128));
    }
}
