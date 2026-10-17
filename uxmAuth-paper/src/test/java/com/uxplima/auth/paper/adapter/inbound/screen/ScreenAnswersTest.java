package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** A press on a screen goes where a command would, and a refusal comes back on the same screen. */
final class ScreenAnswersTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private final List<DialogSpec> shown = new ArrayList<>();
    private int closed;
    private ScreenSettings settings;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        settings = new com.uxplima.auth.adapter.outbound.ScreenFile(folder.resolve("screens.conf")).current();
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the right password on the login screen lets the player in and closes the screen")
    void aLoginAnswerLetsThePlayerIn() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(answers().answer(steve, AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong password comes back on the login screen with the tries left on top")
    void aWrongPasswordShowsTheScreenAgain() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(answers().answer(steve, AuthScreens.LOGIN, answer(Map.of("password", "nope1234"))));

        assertThat(shown).hasSize(1);
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(
                        steve, Keys.LOGIN_WRONG, com.uxplima.uxmlib.text.Text.placeholder("left", "2"))));
    }

    @Test
    @DisplayName("a registration with both passwords makes the account")
    void aRegisterAnswerMakesTheAccount() {
        world.unregistered(steve);

        world.scheduler.settle(answers()
                .answer(steve, AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.REGISTERED);
    }

    @Test
    @DisplayName("with the rules on, an unticked box is refused before anything is hashed")
    void rulesNeedTheBox() {
        world.unregistered(steve);
        settings = new ScreenSettings(
                settings.waitingRoom(),
                settings.cancel(),
                settings.canClose(),
                settings.pinPad(),
                new ScreenSettings.Rules(true, List.of("@rules.line-1")),
                settings.register(),
                settings.login(),
                settings.pin());

        world.scheduler.settle(answers()
                .answer(steve, AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(steve, Keys.REGISTER_RULES_NEEDED)));
    }

    @Test
    @DisplayName("leaving the screen kicks, or lets the player into the frozen world, as screens.conf says")
    void cancelFollowsTheFile() {
        world.registered(steve, "hunter22");
        settings = withCancel(ScreenSettings.Cancel.WORLD);

        world.scheduler.settle(answers().answer(steve, AuthScreens.CANCEL, answer(Map.of())));
        assertThat(steve.isOnline()).isTrue();
        assertThat(closed).isEqualTo(1);

        settings = withCancel(ScreenSettings.Cancel.KICK);
        world.scheduler.settle(answers().answer(steve, AuthScreens.CANCEL, answer(Map.of())));
        assertThat(steve.isOnline()).isFalse();
    }

    private ScreenSettings withCancel(ScreenSettings.Cancel cancel) {
        return new ScreenSettings(
                settings.waitingRoom(),
                cancel,
                settings.canClose(),
                settings.pinPad(),
                settings.rules(),
                settings.register(),
                settings.login(),
                settings.pin());
    }

    private ScreenAnswers answers() {
        AuthScreens screens =
                new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
        ScreenShower shower = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
            }

            @Override
            public void close(Audience who) {
                closed++;
            }
        };
        return new ScreenAnswers(
                screens,
                world.register,
                world.logIn,
                world.door,
                world.moments,
                () -> settings,
                () -> world.passwords,
                world.messages,
                world.replies,
                shower);
    }

    private static ScreenAnswers.Answer answer(Map<String, String> texts) {
        return new ScreenAnswers.Answer() {
            @Override
            public @Nullable String text(String key) {
                return texts.get(key);
            }

            @Override
            public boolean ticked(String key) {
                return false;
            }
        };
    }

    private static String plain(net.kyori.adventure.text.Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }
}
