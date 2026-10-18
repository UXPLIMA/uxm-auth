package com.uxplima.auth.paper.adapter.inbound.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.TimeWords;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
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

/** Each alert reaches the staff who hold the alerts node, on their own thread, and nobody else. */
final class AlertDeliveryTest {

    private static final String HOME = "203.0.113.7";

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock staff;
    private PlayerMock player;
    private final LocalEvents events = new LocalEvents();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        staff = server.addPlayer("Staff");
        staff.addAttachment(MockBukkit.createMockPlugin(), AlertDelivery.PERMISSION, true);
        player = server.addPlayer("Player");
        events.listen(new AlertDelivery(server::getOnlinePlayers, world.scheduler, world.messages));
        events.listen(new RemovedAccounts(server::getPlayer, world.scheduler, world.messages));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void heard(Bulletin alert, Keys key, TagResolver... resolvers) {
        events.publish(alert);
        assertThat(staff.nextComponentMessage())
                .as("nothing is sent before the holder's own thread runs")
                .isNull();
        world.scheduler.drain();
        assertThat(staff.nextComponentMessage()).isEqualTo(world.messages.render(staff, key, resolvers));
        assertThat(staff.nextComponentMessage()).isNull();
        assertThat(player.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("a wrong password and a ban reach the holder with the address and the time")
    void failuresAndBansReachTheHolder() {
        heard(new Bulletin.FailedLogin(HOME), Keys.ALERT_FAILED_LOGIN, Text.placeholder("address", HOME));
        heard(
                new Bulletin.Banned(HOME, Duration.ofMinutes(5)),
                Keys.ALERT_BANNED,
                Text.placeholder("address", HOME),
                new TimeWords(world.messages).placeholder(staff, Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("an address many accounts use reaches the holder with the name and the count")
    void aSharedAddressReachesTheHolder() {
        heard(
                new Bulletin.SharedAddress(new PlayerName("Third"), HOME, 3),
                Keys.ALERT_SHARED_ADDRESS,
                Text.placeholder("name", "Third"),
                Text.placeholder("address", HOME),
                Text.placeholder("count", "3"));
    }

    @Test
    @DisplayName("what staff did reaches the holder with who did it, the console in the reader's words")
    void staffActionsReachTheHolder() {
        heard(
                new Bulletin.StaffActed(Optional.of(new PlayerName("Mod")), "unregister", "Steve"),
                Keys.ALERT_STAFF_ACTED,
                Text.placeholder("actor", "Mod"),
                Text.placeholder("action", "unregister"),
                Text.placeholder("target", "Steve"));
        heard(
                new Bulletin.StaffActed(Optional.empty(), "unban", HOME),
                Keys.ALERT_STAFF_ACTED,
                Placeholder.component("actor", world.messages.render(staff, Keys.ALERT_CONSOLE)),
                Text.placeholder("action", "unban"),
                Text.placeholder("target", HOME));
    }

    @Test
    @DisplayName("a holder who lost the node before their thread ran hears nothing, and other bulletins say nothing")
    void onlyAlertsReachOnlyHolders() {
        events.publish(new Bulletin.FailedLogin(HOME));
        staff.addAttachment(MockBukkit.createMockPlugin(), AlertDelivery.PERMISSION, false);
        world.scheduler.drain();
        assertThat(staff.nextComponentMessage()).isNull();

        events.publish(new Bulletin.SessionsEnded(java.util.UUID.randomUUID(), Optional.empty()));
        world.scheduler.drain();
        assertThat(staff.nextComponentMessage()).isNull();
        assertThat(player.nextComponentMessage()).isNull();
    }
}
