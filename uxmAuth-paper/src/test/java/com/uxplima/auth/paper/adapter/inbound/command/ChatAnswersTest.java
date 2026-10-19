package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A waiting player may answer in chat what the commands ask, and nobody else reads it. */
final class ChatAnswersTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private boolean on = true;
    private final List<String> codes = new ArrayList<>();
    private final List<String> captchas = new ArrayList<>();
    private final Set<java.util.UUID> captchaWaiting = new HashSet<>();
    private ChatAnswers chat;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        RegisterCommand register = new RegisterCommand(
                world.register,
                world.door,
                world::openingOf,
                AuthWorld::shippedPasswords,
                world.messages,
                world.replies,
                (reader, id, reason) -> {});
        LoginCommand login = new LoginCommand(
                world.logIn, world.door, world::openingOf, world.moments, world.messages, world.replies);
        chat = new ChatAnswers(
                world.sessions,
                () -> on,
                () -> AuthWorld.shippedPasswords().confirmOnRegister(),
                new ChatAnswers.Answers(
                        login::attempt,
                        register::attempt,
                        (player, code) -> {
                            codes.add(code);
                            return CompletableFuture.completedFuture(null);
                        },
                        (player, code) -> {
                            captchas.add(code);
                            return CompletableFuture.completedFuture(null);
                        }),
                captchaWaiting::contains,
                world.messages);
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private void drain() {
        while (steve.nextComponentMessage() != null) {
            // Nothing before the part under test counts.
        }
    }

    private void type(String line) {
        Optional<CompletableFuture<Void>> taken = chat.take(steve, line);
        assertThat(taken).as("taken: " + line).isPresent();
        world.scheduler.settle(taken.orElseThrow());
    }

    @Test
    @DisplayName("a registered player types the password in chat and is in")
    void aPasswordInChatLogsIn() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();

        type("hunter22");

        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("a new player types the password, is asked for it again, and is registered by the second")
    void aNewPlayerRegistersInTwoLines() {
        world.unregistered(steve);
        drain();

        type("hunter22");
        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CHAT_REGISTER_REPEAT));

        type("hunter22");
        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("a repeat that does not match is refused, and the next line starts over")
    void aMismatchStartsOver() {
        world.unregistered(steve);
        drain();

        type("hunter22");
        type("hunter23");
        assertThat(world.isAuthenticated(steve)).isFalse();
        drain();

        type("secret99");
        assertThat(steve.nextComponentMessage())
                .as("the line after a mismatch is a new first password, asked again")
                .isEqualTo(world.messages.render(steve, Keys.CHAT_REGISTER_REPEAT));
        type("secret99");
        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("while a captcha waits, the line answers the captcha and is never taken for a password")
    void aWaitingCaptchaTakesTheLine() {
        world.registered(steve, "hunter22");
        captchaWaiting.add(steve.getUniqueId());

        type("hunter22");

        assertThat(captchas).containsExactly("hunter22");
        assertThat(world.isAuthenticated(steve)).isFalse();

        captchaWaiting.clear();
        type("hunter22");
        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("the code of the second step is answered from chat")
    void theCodeIsAnsweredFromChat() {
        world.registered(steve, "hunter22");
        world.sessions.update(
                steve.getUniqueId(),
                state -> new AuthState.SecondFactor(
                        ((AuthState.Registered) state).account(),
                        com.uxplima.auth.domain.state.AuthMethod.PASSWORD,
                        Set.of(com.uxplima.auth.domain.factor.FactorKind.APP),
                        com.uxplima.auth.domain.state.Attempts.fresh(3),
                        null));

        type(" 123456 ");

        assertThat(codes).containsExactly("123456");
    }

    @Test
    @DisplayName("chat is left alone when it is off, for a player who is in, and for one this plugin never held")
    void chatIsLeftAlone() {
        world.registered(steve, "hunter22");
        on = false;
        assertThat(chat.take(steve, "hunter22")).isEmpty();

        on = true;
        PlayerMock alex = server.addPlayer("Alex");
        assertThat(chat.take(alex, "hello")).isEmpty();

        world.scheduler.settle(chat.take(steve, "hunter22").orElseThrow());
        assertThat(chat.take(steve, "hello everyone")).isEmpty();
    }

    @Test
    @DisplayName("the chat event of a waiting player is cancelled and reaches nobody")
    void theEventReachesNobody() {
        world.registered(steve, "hunter22");
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        Set<Audience> viewers = new HashSet<>(Set.of(server.addPlayer("Alex")));
        when(event.getPlayer()).thenReturn(steve);
        when(event.message()).thenReturn(Component.text("hunter22"));
        when(event.signedMessage()).thenReturn(mock(SignedMessage.class));
        when(event.viewers()).thenReturn(viewers);

        chat.onChat(event);
        // The login runs on the lane and answers on the player's thread; a task behind it on the lane waits for it.
        world.scheduler.settle(world.lane.submit(() -> true));
        world.scheduler.drain();

        verify(event).setCancelled(true);
        assertThat(viewers).isEmpty();
        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("an authenticated player's chat event is not touched")
    void anAuthenticatedPlayersEventIsNotTouched() {
        world.registered(steve, "hunter22");
        world.scheduler.settle(chat.take(steve, "hunter22").orElseThrow());
        AsyncChatEvent event = mock(AsyncChatEvent.class);
        when(event.getPlayer()).thenReturn(steve);
        when(event.message()).thenReturn(Component.text("hi"));

        chat.onChat(event);

        verify(event, never()).setCancelled(true);
    }

    @Test
    @DisplayName("a first password waiting for its repeat is let go when its player leaves")
    void aPlayerWhoLeftIsLetGo() {
        world.unregistered(steve);
        type("hunter22");
        world.sessions.close(steve.getUniqueId());

        PlayerMock alex = server.addPlayer("Alex");
        world.sessions.open(alex.getUniqueId(), new AuthState.Unregistered(new PlayerName("Alex")));
        world.scheduler.settle(chat.take(alex, "secret99").orElseThrow());

        assertThat(chat.waiting()).isEqualTo(1);
    }
}
