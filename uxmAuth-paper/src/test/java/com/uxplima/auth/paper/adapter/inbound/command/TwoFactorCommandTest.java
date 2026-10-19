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
    @DisplayName("/2fa confirm, off, backup or email off alone reaches the command, says how and counts nothing")
    void aBareSubcommandSaysHow() throws java.io.IOException {
        world.registered(steve, "hunter22");
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        world.appOn(steve);
        java.nio.file.Path file = folder.resolve("commands.conf");
        try (java.io.InputStream shipped =
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/commands.conf"))) {
            java.nio.file.Files.copy(shipped, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        var node = com.uxplima.uxmlib.command.annotation.AnnotatedCommands.buildNode(
                command,
                com.uxplima.uxmlib.command.annotation.ParamResolvers.withDefaults()
                        .replacer(
                                com.uxplima.uxmlib.command.annotation.annotations.FromConfig.class,
                                com.uxplima.uxmlib.command.annotation.ConfiguredCommands.load(file)
                                        .replacer()));
        for (String word : List.of("confirm", "off", "backup")) {
            assertThat(node.getChild(word).getCommand()).as(word).isNotNull();
        }
        assertThat(node.getChild("email").getChild("off").getCommand()).isNotNull();

        var setup = world.factorSetup();
        var mail = world.mailToggle();
        // Twelve rounds of three bare lines: counted as wrong codes they would ban the address many times over.
        for (int round = 0; round < 12; round++) {
            world.scheduler.settle(setup.off(steve, ""));
            world.scheduler.settle(setup.backup(steve, ""));
            world.scheduler.settle(mail.off(steve, ""));
        }
        world.scheduler.settle(setup.confirm(steve, ""));

        Component usage = world.messages.render(steve, Keys.FACTOR_USAGE);
        for (int line = 0; line < 37; line++) {
            assertThat(steve.nextComponentMessage()).as("line %d", line).isEqualTo(usage);
        }
        assertThat(steve.isOnline()).isTrue();
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
