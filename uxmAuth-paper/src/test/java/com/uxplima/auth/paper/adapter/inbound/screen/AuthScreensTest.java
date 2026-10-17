package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The screens are built from screens.conf and config.conf, and nothing on them is decided in code. */
final class AuthScreensTest {

    private ServerMock server;
    private PlayerMock steve;
    private Messages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        steve = server.addPlayer("Steve");
        messages = new Messages(
                new MessageCatalog(
                        Map.of(
                                Locale.ENGLISH,
                                Map.of(
                                        "screen.register.title", "Welcome",
                                        "screen.register.body", "Choose a password",
                                        "screen.register.password", "Password",
                                        "screen.register.confirm", "Again",
                                        "screen.register.accept", "I accept",
                                        "screen.register.submit", "Register",
                                        "screen.register.cancel", "Leave",
                                        "rules.line-1", "Be kind",
                                        "screen.login.title", "Welcome back",
                                        "screen.login.password", "Password")),
                        Locale.ENGLISH),
                LocaleSource.ofDefault(Locale.ENGLISH));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the register screen asks twice while the confirmation is on, and has the shipped buttons")
    void registerAsksTwice() {
        DialogSpec spec = screens(shipped(false), passwords(true)).register(steve, null);

        assertThat(plain(spec.title())).isEqualTo("Welcome");
        assertThat(spec.inputs()).extracting(DialogSpec.Input::key).containsExactly("password", "confirm");
        assertThat(spec.buttons()).extracting(DialogSpec.Button::action).containsExactly("register", "cancel");
        assertThat(spec.canClose()).isFalse();
    }

    @Test
    @DisplayName("with the confirmation off there is one field")
    void registerAsksOnceWhenTold() {
        DialogSpec spec = screens(shipped(false), passwords(false)).register(steve, null);

        assertThat(spec.inputs()).extracting(DialogSpec.Input::key).containsExactly("password");
    }

    @Test
    @DisplayName("with the rules on, they are shown above the body and a box must be ticked")
    void rulesComeFirstAndNeedTheBox() {
        DialogSpec spec = screens(shipped(true), passwords(true)).register(steve, null);

        assertThat(spec.body()).extracting(AuthScreensTest::plain).containsExactly("Be kind", "Choose a password");
        assertThat(spec.inputs()).extracting(DialogSpec.Input::key).containsExactly("password", "confirm", "accept");
    }

    @Test
    @DisplayName("a reason a password was refused is the first line, so the player reads it before anything else")
    void theReasonComesFirst() {
        DialogSpec spec = screens(shipped(false), passwords(true)).register(steve, Component.text("Too short"));

        assertThat(spec.body()).extracting(AuthScreensTest::plain).startsWith("Too short");
    }

    @Test
    @DisplayName("an operator's own words are drawn as written, beside the catalogue's")
    void ownWordsAreDrawn() {
        ScreenSettings custom = new ScreenSettings(
                ScreenSettings.WaitingRoom.DIALOG,
                ScreenSettings.Cancel.KICK,
                true,
                false,
                new ScreenSettings.Rules(false, List.of()),
                shipped(false).register(),
                new ScreenSettings.Screen(
                        "<gold>Survival",
                        List.of("Our discord: example.org"),
                        Map.of("password", "@screen.login.password")),
                shipped(false).pin());

        DialogSpec spec = screens(custom, passwords(true)).login(steve, null);

        assertThat(plain(spec.title())).isEqualTo("Survival");
        assertThat(spec.body()).extracting(AuthScreensTest::plain).containsExactly("Our discord: example.org");
        assertThat(spec.inputs()).extracting(input -> plain(input.label())).containsExactly("Password");
        assertThat(spec.canClose()).isTrue();
    }

    @Test
    @DisplayName("the password field is as long as a password may be")
    void theFieldFitsTheLongestPassword() {
        DialogSpec spec = screens(shipped(false), passwords(true)).login(steve, null);

        assertThat(spec.inputs().get(0))
                .isInstanceOfSatisfying(
                        DialogSpec.TextInput.class,
                        field -> assertThat(field.maxLength()).isEqualTo(64));
    }

    private AuthScreens screens(ScreenSettings settings, AuthSettings.Passwords passwords) {
        return new AuthScreens(() -> settings, () -> passwords, messages, new Styler(Theme.defaults()));
    }

    private static ScreenSettings shipped(boolean rules) {
        return new ScreenSettings(
                ScreenSettings.WaitingRoom.DIALOG,
                ScreenSettings.Cancel.KICK,
                false,
                false,
                new ScreenSettings.Rules(rules, List.of("@rules.line-1")),
                screen("register", "password", "confirm", "accept", "submit", "cancel"),
                screen("login", "password", "submit", "cancel"),
                screen("pin", "erase", "enter", "cancel"));
    }

    private static ScreenSettings.Screen screen(String name, String... labels) {
        java.util.Map<String, String> named = new java.util.LinkedHashMap<>();
        for (String label : labels) {
            named.put(label, "@screen." + name + "." + label);
        }
        return new ScreenSettings.Screen("@screen." + name + ".title", List.of("@screen." + name + ".body"), named);
    }

    private static AuthSettings.Passwords passwords(boolean confirm) {
        return new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), confirm, true, 3);
    }

    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }
}
