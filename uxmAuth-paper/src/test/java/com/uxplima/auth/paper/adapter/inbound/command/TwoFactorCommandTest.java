package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.screen.FactorOffer;
import com.uxplima.auth.paper.adapter.inbound.screen.Guest;
import com.uxplima.auth.paper.adapter.inbound.screen.PlayerGuest;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** {@code /2fa <code>}: the code of the second step typed as a command, where a screen is not in use. */
final class TwoFactorCommandTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private final List<String> answered = new ArrayList<>();
    private TwoFactorCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        FactorOffer offer = new FactorOffer() {
            @Override
            public void offer(Audience reader, @Nullable Component reason) {}

            @Override
            public CompletableFuture<Void> answer(Guest guest, String code) {
                answered.add(guest.name() + ":" + code);
                return CompletableFuture.completedFuture(null);
            }
        };
        command = new TwoFactorCommand(
                world.factorSetup(),
                new com.uxplima.auth.paper.adapter.inbound.factor.FactorLogin(
                        world.sessions,
                        offer,
                        player -> new PlayerGuest(
                                player, world.openingOf(player), world.door, world.moments, world.replies),
                        world.messages),
                world.mailToggle());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("a player waiting for the code hands it to the second step")
    void aWaitingPlayerHandsTheCode() {
        world.registered(steve, "hunter22");
        TotpSecret secret = world.appOn(steve);
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isEqualTo(new LoginResult.CodeNeeded());

        world.scheduler.settle(command.answer(steve, world.codeNow(secret)));

        assertThat(answered).containsExactly("Steve:" + world.codeNow(secret));
    }

    @Test
    @DisplayName("a code from a player no code is asked of is told so, and nothing is answered")
    void nothingIsAsked() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(command.answer(steve, "123456"));

        assertThat(answered).isEmpty();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_NOTHING_ASKED));
    }

    @Test
    @DisplayName("the command alone says what it does")
    void aloneItSaysWhatItDoes() {
        world.scheduler.settle(command.answer(steve, ""));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_USAGE));
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);
    }
}
