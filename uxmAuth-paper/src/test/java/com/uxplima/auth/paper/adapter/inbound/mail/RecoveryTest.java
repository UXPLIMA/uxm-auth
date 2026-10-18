package com.uxplima.auth.paper.adapter.inbound.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.paper.adapter.inbound.command.Addresses;
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

/** {@code /recover}: a code to the linked address at the login, and the code with a new password sets it. */
final class RecoveryTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private Recovery recovery;
    private final Set<UUID> bedrock = new HashSet<>();
    private @Nullable BiConsumer<String, String> form;
    private int forms;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        recovery = world.recovery(
                (player, answer) -> {
                    form = answer;
                    forms++;
                },
                bedrock::contains);
        world.registered(steve, "hunter22");
        world.emails.setAddress(
                account(), EmailAddress.parse("berke@example.com").orElseThrow());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private UUID account() {
        return world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id();
    }

    private String mailedCode() {
        return Objects.requireNonNull(world.mailed.getLast().get("code"), "code");
    }

    @Test
    @DisplayName("at the login, /recover mails a code and says where, masked, and for how long")
    void aCodeIsMailed() {
        world.scheduler.settle(recovery.request(steve));

        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(
                        steve,
                        Keys.RECOVER_SENT,
                        Text.placeholder("address", "b***@e***.com"),
                        Text.placeholder("minutes", "15")));
        assertThat(form).as("a Java player types the command; no form is drawn").isNull();
    }

    @Test
    @DisplayName("the code and a new password set it; the player logs in with the new one")
    void theCodeSetsThePassword() {
        world.scheduler.settle(recovery.request(steve));
        steve.nextComponentMessage();

        world.scheduler.settle(recovery.recover(steve, mailedCode(), "fresh7777"));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_DONE));
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("fresh7777"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    @Test
    @DisplayName("a second request inside the cooldown says when; a wrong code says the tries left")
    void cooldownAndWrong() {
        world.scheduler.settle(recovery.request(steve));
        steve.nextComponentMessage();

        world.scheduler.settle(recovery.request(steve));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.RECOVER_COOLDOWN, Text.placeholder("seconds", "60")));
        world.scheduler.settle(recovery.recover(steve, "000000", "fresh7777"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.RECOVER_WRONG, Text.placeholder("left", "4")));
    }

    @Test
    @DisplayName("no linked address, or a player logged in already, is told so")
    void refusals() {
        world.emails.removeAddress(account());
        world.scheduler.settle(recovery.request(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_NO_ADDRESS));

        world.logIn
                .run(steve.getUniqueId(), Password.of("hunter22"), "127.0.0.1")
                .join();
        world.scheduler.settle(recovery.request(steve));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_NOT_WAITING));
    }

    @Test
    @DisplayName("a code without a new password, or a password without a code, says how to type it and spends nothing")
    void halfTypedSaysHow() {
        world.scheduler.settle(recovery.request(steve));
        steve.nextComponentMessage();

        world.scheduler.settle(recovery.recover(steve, mailedCode(), ""));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_USAGE));
        world.scheduler.settle(recovery.recover(steve, "", "fresh7777"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_USAGE));

        world.scheduler.settle(recovery.recover(steve, mailedCode(), "fresh7777"));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RECOVER_DONE));
    }

    @Test
    @DisplayName("enough wrong codes ban the address and send the player away")
    void wrongCodesBan() {
        String address = Addresses.of(steve);
        for (int round = 0; round < 4 && steve.isOnline(); round++) {
            world.mailNow = world.mailNow.plusSeconds(120);
            world.scheduler.settle(recovery.request(steve));
            for (int i = 0; i < 5 && steve.isOnline(); i++) {
                world.scheduler.settle(recovery.recover(steve, "000000", "fresh7777"));
            }
        }

        assertThat(steve.isOnline()).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.BANNED);
        assertThat(world.bans.rule().check(new com.uxplima.auth.application.protection.Admission.Arrival(address, "")))
                .as("the ban falls on the address the player came from")
                .isPresent();
    }

    @Test
    @DisplayName("a Bedrock player types the code and the new password into a form after the code is sent")
    void aBedrockPlayerUsesAForm() {
        bedrock.add(steve.getUniqueId());

        world.scheduler.settle(recovery.request(steve));
        Objects.requireNonNull(form).accept(mailedCode(), "fresh7777");
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline
                && !(world.sessions.state(steve.getUniqueId()).orElseThrow()
                                instanceof com.uxplima.auth.domain.state.AuthState.Registered held
                        && world.hashing.verify(
                                        Password.of("fresh7777"),
                                        Objects.requireNonNull(held.account().password()))
                                instanceof com.uxplima.auth.domain.hash.Verification.Right)) {
            world.scheduler.drain();
            Thread.onSpinWait();
        }

        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("fresh7777"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
        world.scheduler.drain();
        assertThat(forms)
                .as("the form follows the sent code, not the answer to it")
                .isEqualTo(1);
    }
}
