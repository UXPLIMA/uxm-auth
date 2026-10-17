package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.domain.state.AuthState;
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

/** A ten-key pad instead of a typed password: digits pressed one at a time, held per connection, wiped on exit. */
final class PinPadTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private ScreenSettings settings;
    private final List<DialogSpec> shown = new ArrayList<>();
    private ScreenAnswers answers;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.getPluginManager().addPermission(new Permission("uxmauth.login", PermissionDefault.TRUE));
        server.getPluginManager().addPermission(new Permission("uxmauth.register", PermissionDefault.TRUE));
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        ScreenSettings shipped = new ScreenFile(folder.resolve("screens.conf")).current();
        settings = new ScreenSettings(
                shipped.waitingRoom(),
                ScreenSettings.Cancel.WORLD,
                shipped.canClose(),
                true,
                shipped.rules(),
                shipped.register(),
                shipped.login(),
                shipped.pin());
        answers = answers();
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("with the pad on, the login screen is ten digits, erase and enter, and no text field")
    void thePadReplacesThePasswordField() {
        DialogSpec pad = screens().login(steve, null);

        assertThat(pad.inputs()).isEmpty();
        assertThat(pad.buttons())
                .extracting(DialogSpec.Button::action)
                .contains(
                        PinPad.digit(0),
                        PinPad.digit(5),
                        PinPad.digit(9),
                        PinPad.ERASE,
                        PinPad.ENTER,
                        AuthScreens.CANCEL);
        assertThat(pad.columns()).isEqualTo(3);
    }

    @Test
    @DisplayName("each press shows the pad again with one more digit masked, and enter logs in")
    void pressesBuildTheCode() {
        world.registered(steve, "123456");

        press("1", "2");
        assertThat(lastLine()).contains("●●").doesNotContain("●●●");
        press("3", "4", "5", "6");
        world.scheduler.settle(answers.answer(guest(), PinPad.ENTER, nothing()));

        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("erase takes back the last digit")
    void eraseRemovesTheLast() {
        world.registered(steve, "123456");

        press("1", "2", "3", "4", "5", "6", "7");
        world.scheduler.settle(answers.answer(guest(), PinPad.ERASE, nothing()));
        world.scheduler.settle(answers.answer(guest(), PinPad.ENTER, nothing()));

        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("a registration asks for the code twice while the file asks for a confirmation")
    void aRegistrationAsksTwice() {
        world.unregistered(steve);

        press("1", "2", "3", "4", "5", "6");
        world.scheduler.settle(answers.answer(guest(), PinPad.ENTER, nothing()));
        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(lastLine()).doesNotContain("●");

        press("1", "2", "3", "4", "5", "6");
        world.scheduler.settle(answers.answer(guest(), PinPad.ENTER, nothing()));

        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Authenticated.class);
    }

    @Test
    @DisplayName("the digits are wiped on enter, and dropped on leaving the pad and on leaving the server")
    void theDigitsAreWipedOnEveryExit() {
        world.registered(steve, "123456");
        press("9", "9");
        char[] held = answers.pinPad().peek(steve.getUniqueId()).orElseThrow();

        world.scheduler.settle(answers.answer(guest(), PinPad.ENTER, nothing()));
        assertThat(held).containsOnly('\0');
        assertThat(answers.pinPad().peek(steve.getUniqueId())).isEmpty();

        press("1");
        world.scheduler.settle(answers.answer(guest(), AuthScreens.CANCEL, nothing()));
        assertThat(answers.pinPad().peek(steve.getUniqueId())).isEmpty();

        press("1");
        answers.forget(steve.getUniqueId());
        assertThat(answers.pinPad().peek(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("a player who leaves the server drops what they pressed")
    void aQuitDropsThePad() {
        world.registered(steve, "123456");
        press("1");

        new ScreenClickListener(answers, player -> guest(), world.scheduler)
                .onQuit(new PlayerQuitEvent(steve, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));

        assertThat(answers.pinPad().peek(steve.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("no more digits are taken than the longest password the file allows")
    void theCodeStopsAtTheLongestPassword() {
        world.registered(steve, "123456");

        for (int i = 0; i < AuthWorld.shippedPasswords().maxLength() + 5; i++) {
            press("1");
        }

        assertThat(answers.pinPad().peek(steve.getUniqueId()).orElseThrow())
                .hasSize(AuthWorld.shippedPasswords().maxLength());
    }

    private void press(String... digits) {
        for (String digit : digits) {
            world.scheduler.settle(answers.answer(guest(), PinPad.digit(Integer.parseInt(digit)), nothing()));
        }
    }

    private String lastLine() {
        List<Component> body = shown.get(shown.size() - 1).body();
        return PlainTextComponentSerializer.plainText().serialize(body.get(body.size() - 1));
    }

    private AuthScreens screens() {
        return new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
    }

    private ScreenAnswers answers() {
        ScreenShower shower = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
            }

            @Override
            public void close(Audience who) {}
        };
        return new ScreenAnswers(
                screens(),
                world.register,
                world.logIn,
                () -> settings,
                () -> world.passwords,
                world.messages,
                shower,
                id -> world.sessions.state(id).orElse(null) instanceof AuthState.Unregistered);
    }

    private Guest guest() {
        return new PlayerGuest(steve, world.openingOf(steve), world.door, world.moments, world.replies);
    }

    private static ScreenAnswers.Answer nothing() {
        return new ScreenAnswers.Answer() {
            @Override
            public @Nullable String text(String key) {
                return null;
            }

            @Override
            public boolean ticked(String key) {
                return false;
            }
        };
    }
}
