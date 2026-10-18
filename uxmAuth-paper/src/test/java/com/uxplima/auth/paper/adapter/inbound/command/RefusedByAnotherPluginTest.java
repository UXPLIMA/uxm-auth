package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.api.paper.event.FailedLoginEvent;
import com.uxplima.auth.api.paper.event.PreLoginEvent;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.screen.FactorOffer;
import com.uxplima.auth.paper.adapter.inbound.screen.Guest;
import com.uxplima.auth.paper.adapter.outbound.event.PaperEvents;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Another plugin refuses a login before its password is checked: the player reads that plugin's reason, no attempt
 * is spent and no wrong password is counted against the address.
 */
final class RefusedByAnotherPluginTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private final List<String> counted = new CopyOnWriteArrayList<>();
    private final List<Integer> failed = new CopyOnWriteArrayList<>();
    private volatile String refusal = "Closed for maintenance.";
    private LoginCommand login;

    /** Somebody else's plugin. */
    public final class Gate implements Listener {
        @EventHandler
        public void before(PreLoginEvent event) {
            if (!refusal.isEmpty()) {
                event.refuse(refusal);
            }
        }

        @EventHandler
        public void wrong(FailedLoginEvent event) {
            failed.add(event.attemptsLeft());
        }
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        server.getPluginManager().registerEvents(new Gate(), MockBukkit.createMockPlugin());
        steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        LogIn guarded = new LogIn(
                world.accounts,
                world.hashing,
                world.lane,
                world.sessions,
                world.clock,
                (address, host) -> {
                    counted.add(address);
                    return Optional.empty();
                },
                () -> world.policy,
                LogIn.Captcha.NONE,
                SecondStep.NONE,
                new PaperEvents(server::getPlayer, server.getPluginManager()::callEvent));
        login = new LoginCommand(
                guarded,
                world.door,
                world::openingOf,
                world.moments,
                world.messages,
                world.replies,
                (reader, id, reason) -> {},
                new FactorOffer() {
                    @Override
                    public void offer(Audience reader, @Nullable Component reason) {}

                    @Override
                    public CompletableFuture<Void> answer(Guest guest, String code) {
                        return CompletableFuture.completedFuture(null);
                    }
                });
        while (steve.nextComponentMessage() != null) {
            // What the join said is not under test.
        }
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private int attemptsLeft() {
        return ((AuthState.Registered) world.sessions.state(steve.getUniqueId()).orElseThrow())
                .attempts()
                .left();
    }

    @Test
    @DisplayName("a refused login reads the other plugin's reason, spends no attempt and counts nothing")
    void aRefusedLoginCostsNothing() {
        world.scheduler.settle(login.attempt(steve, "wrong123"));

        assertThat(steve.nextComponentMessage()).isEqualTo(Component.text("Closed for maintenance."));
        assertThat(attemptsLeft()).isEqualTo(AuthWorld.MAX_ATTEMPTS);
        assertThat(counted).isEmpty();
        assertThat(failed).isEmpty();
    }

    @Test
    @DisplayName("a refusal with no reason reads our own words")
    void aBareRefusalReadsOurs() {
        refusal = " ";
        world.scheduler.settle(login.attempt(steve, "wrong123"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGIN_REFUSED));
    }

    @Test
    @DisplayName("once nobody refuses, a wrong password is counted and fired as before")
    void anAllowedLoginGoesOn() {
        refusal = "";
        world.scheduler.settle(login.attempt(steve, "wrong123"));

        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve, Keys.LOGIN_WRONG, Text.placeholder("left", String.valueOf(AuthWorld.MAX_ATTEMPTS - 1))));
        assertThat(counted).hasSize(1);
        assertThat(failed).containsExactly(AuthWorld.MAX_ATTEMPTS - 1);
    }
}
