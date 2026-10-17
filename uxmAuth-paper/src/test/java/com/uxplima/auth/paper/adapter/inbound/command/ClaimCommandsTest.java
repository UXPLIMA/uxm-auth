package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.ClaimName;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** {@code /claim} and {@code /premium}: the player is sent away to prove the name on their next connection. */
final class ClaimCommandsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private AtTheDoor door;
    private boolean available = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        door = new AtTheDoor(
                world.accounts,
                AuthWorld::shippedNames,
                new PremiumLookup(List::of, () -> Duration.ofMinutes(30), world.clock),
                () -> new NameOwnership(
                        NameRule.AUTOMATIC,
                        WhenUnknown.CRACKED,
                        new HostPatterns(List.of()),
                        new HostPatterns(List.of())),
                new NameMemory(() -> Duration.ofMinutes(5), world.clock),
                new NameMemory(() -> Duration.ofMinutes(10), world.clock),
                new NameMemory(() -> Duration.ofMinutes(10), world.clock),
                new SqlDisputes(world.database));
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private ClaimName claim() {
        return new ClaimName(world.sessions, door, () -> NameRule.AUTOMATIC);
    }

    private ClaimCommand claimCommand() {
        return new ClaimCommand(claim(), () -> available, world.messages);
    }

    private PremiumCommand premiumCommand() {
        return new PremiumCommand(claim(), () -> available, world.sessions, world.messages);
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static String said(PlayerMock player) {
        return plain(Objects.requireNonNull(player.nextComponentMessage()));
    }

    @Test
    @DisplayName("/claim at the door sends the player away to come back from the official launcher, from here")
    void claimSendsThePlayerAway() {
        world.registered(steve, "hunter22");
        String here = Objects.requireNonNull(steve.getAddress()).getAddress().getHostAddress();

        claimCommand().attempt(steve);

        assertThat(steve.isOnline()).isFalse();
        assertThat(door.decide("Steve", here, "h")).isEqualTo(new com.uxplima.auth.domain.premium.Decision.Handshake());
    }

    @Test
    @DisplayName("/claim where the server cannot ask Mojang says there is nothing to claim, and keeps the player")
    void claimWithoutTheHandshakeKeepsThePlayer() {
        available = false;
        world.registered(steve, "hunter22");

        claimCommand().attempt(steve);

        assertThat(steve.isOnline()).isTrue();
        assertThat(said(steve)).contains("cannot ask Mojang");
    }

    @Test
    @DisplayName("/premium before a login asks for the login first, and asks nothing of the next connection")
    void premiumNeedsALogin() {
        world.registered(steve, "hunter22");

        premiumCommand().attempt(steve);

        assertThat(steve.isOnline()).isTrue();
        assertThat(said(steve)).contains("Log in first");
        assertThat(door.ownerAsked(
                        Objects.requireNonNull(steve.getAddress()).getAddress().getHostAddress(),
                        new PlayerName("Steve")))
                .isFalse();
    }

    @Test
    @DisplayName("/premium logged in asks the owner's next connection, and sends them away to make it")
    void premiumLoggedInAsksTheOwnersNextConnection() {
        world.registered(steve, "hunter22");
        world.scheduler.settle(world.logIn.run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1"));
        String here = Objects.requireNonNull(steve.getAddress()).getAddress().getHostAddress();

        premiumCommand().attempt(steve);

        assertThat(steve.isOnline()).isFalse();
        assertThat(door.ownerAsked(here, new PlayerName("Steve"))).isTrue();
    }
}
