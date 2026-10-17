package com.uxplima.auth.paper.adapter.inbound.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.entity.Player;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The first thing a joining player reads is in their own language, not in the server's default. */
final class GreeterTest {

    private ServerMock server;
    private final List<AuthSettings.Moment> fired = new ArrayList<>();
    private final List<Runnable> later = new ArrayList<>();
    private Greeter greeter;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        greeter = new Greeter(
                (Player player, AuthSettings.Moment moment) -> fired.add(moment), (player, task) -> later.add(task));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the join moment waits for the client to say its language, and runs once")
    void theJoinMomentWaitsForTheClientLanguage() {
        PlayerMock steve = server.addPlayer("Steve");

        greeter.greet(steve, AuthSettings.Moment.JOIN_REGISTERED);
        assertThat(fired).isEmpty();

        greeter.languageKnown(steve);
        later.forEach(Runnable::run);

        assertThat(fired).containsExactly(AuthSettings.Moment.JOIN_REGISTERED);
    }

    @Test
    @DisplayName("a client that never reports a language is greeted anyway, a moment later")
    void aSilentClientIsGreetedAfterTheFallback() {
        PlayerMock steve = server.addPlayer("Steve");

        greeter.greet(steve, AuthSettings.Moment.JOIN_UNREGISTERED);
        later.forEach(Runnable::run);

        assertThat(fired).containsExactly(AuthSettings.Moment.JOIN_UNREGISTERED);
    }

    @Test
    @DisplayName("a language change long after the greeting greets nobody again")
    void aLaterLanguageChangeIsNotAGreeting() {
        PlayerMock steve = server.addPlayer("Steve");
        greeter.greet(steve, AuthSettings.Moment.JOIN_REGISTERED);
        later.forEach(Runnable::run);

        greeter.languageKnown(steve);

        assertThat(fired).containsExactly(AuthSettings.Moment.JOIN_REGISTERED);
    }
}
