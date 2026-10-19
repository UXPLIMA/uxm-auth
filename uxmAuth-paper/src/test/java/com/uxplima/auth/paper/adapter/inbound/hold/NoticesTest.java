package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** What a player is told once they stand in the world, each thing once. */
final class NoticesTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private Notices notices;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        notices = new Notices(world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private static String said(PlayerMock player) {
        return PlainTextComponentSerializer.plainText()
                .serialize(java.util.Objects.requireNonNull(player.nextComponentMessage()));
    }

    @Test
    @DisplayName("a notice for a waiting player is told when they stand in the world, once")
    void aWaitingNoticeIsToldOnce() {
        PlayerMock steve = server.addPlayer("Steve");
        notices.whileWaiting(steve.getUniqueId(), new Notices.Notice(Keys.LOGIN_HANDSHAKE_FAILED, List.of(), () -> {}));

        notices.deliver(steve, false);
        notices.deliver(steve, false);

        assertThat(said(steve)).contains("/claim");
        assertThat(steve.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a notice for after the login waits for it, is told once, and says when it was read")
    void anInNoticeWaitsForTheLogin() {
        PlayerMock steve = server.addPlayer("Notch_");
        AtomicInteger read = new AtomicInteger();
        notices.onceIn(
                steve.getUniqueId(),
                new Notices.Notice(
                        Keys.LOGIN_RENAMED,
                        List.of(Text.placeholder("old", "Notch"), Text.placeholder("new", "Notch_")),
                        read::incrementAndGet));

        notices.deliver(steve, false);
        assertThat(steve.nextComponentMessage()).isNull();

        notices.deliver(steve, true);
        notices.deliver(steve, true);
        assertThat(said(steve)).contains("Notch_");
        assertThat(steve.nextComponentMessage()).isNull();
        assertThat(read).hasValue(1);
    }

    @Test
    @DisplayName("a notice that names a moment runs its list for the player once it is read, once")
    void aNoticeRunsItsMoment() {
        List<com.uxplima.auth.adapter.outbound.AuthSettings.Moment> fired = new java.util.ArrayList<>();
        Notices told = new Notices(world.messages, (player, moment) -> fired.add(moment));
        PlayerMock steve = server.addPlayer("Notch_");
        told.onceIn(
                steve.getUniqueId(),
                new Notices.Notice(
                        Keys.LOGIN_RENAMED,
                        List.of(Text.placeholder("old", "Notch"), Text.placeholder("new", "Notch_")),
                        () -> {},
                        java.util.Optional.of(com.uxplima.auth.adapter.outbound.AuthSettings.Moment.CONFLICT)));

        told.deliver(steve, true);
        told.deliver(steve, true);

        assertThat(fired).containsExactly(com.uxplima.auth.adapter.outbound.AuthSettings.Moment.CONFLICT);
    }

    @Test
    @DisplayName("a player who leaves takes their notices with them")
    void aLeavingPlayerTakesTheirNotices() {
        PlayerMock steve = server.addPlayer("Steve");
        notices.whileWaiting(steve.getUniqueId(), new Notices.Notice(Keys.LOGIN_HANDSHAKE_FAILED, List.of(), () -> {}));

        notices.forget(steve.getUniqueId());
        notices.deliver(steve, true);

        assertThat(steve.nextComponentMessage()).isNull();
    }
}
