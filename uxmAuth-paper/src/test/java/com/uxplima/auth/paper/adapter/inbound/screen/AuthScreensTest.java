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
                                Map.ofEntries(
                                        Map.entry("screen.register.title", "Welcome"),
                                        Map.entry("screen.register.body", "Choose a password"),
                                        Map.entry("screen.register.password", "Password"),
                                        Map.entry("screen.register.confirm", "Again"),
                                        Map.entry("screen.register.accept", "I accept"),
                                        Map.entry("screen.register.submit", "Register"),
                                        Map.entry("screen.register.cancel", "Leave"),
                                        Map.entry("rules.line-1", "Be kind"),
                                        Map.entry("screen.login.title", "Welcome back"),
                                        Map.entry("screen.login.password", "Password"),
                                        Map.entry("screen.captcha.title", "Prove it"),
                                        Map.entry("screen.captcha.body", "Type <code>"),
                                        Map.entry("screen.captcha.code", "Code"),
                                        Map.entry("screen.code.title", "One more step"),
                                        Map.entry("screen.code.body", "Type the app's code"),
                                        Map.entry("screen.code.code", "App code"))),
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
    @DisplayName("the captcha screen shows the code of this showing and asks for it in a field as long as the code")
    void theCaptchaScreenShowsTheCode() {
        DialogSpec spec = screens(shipped(false), passwords(true))
                .captcha(steve, "AB7KQ", net.kyori.adventure.text.Component.text("Solve it first"));

        assertThat(plain(spec.title())).isEqualTo("Prove it");
        assertThat(spec.body()).extracting(AuthScreensTest::plain).containsExactly("Solve it first", "Type AB7KQ");
        assertThat(spec.inputs()).singleElement().isInstanceOfSatisfying(DialogSpec.TextInput.class, field -> {
            assertThat(field.key()).isEqualTo("code");
            assertThat(plain(field.label())).isEqualTo("Code");
            assertThat(field.maxLength()).isEqualTo(5);
        });
        assertThat(spec.buttons()).extracting(DialogSpec.Button::action).containsExactly("captcha", "cancel");
        assertThat(spec.canClose()).isFalse();
    }

    @Test
    @DisplayName(
            "the code screen asks the app's code in one field, long enough for a backup code, with the reason on top")
    void theCodeScreenAsksTheAppsCode() {
        DialogSpec spec = screens(shipped(false), passwords(true))
                .code(steve, net.kyori.adventure.text.Component.text("Wrong code"));

        assertThat(plain(spec.title())).isEqualTo("One more step");
        assertThat(spec.body()).extracting(AuthScreensTest::plain).containsExactly("Wrong code", "Type the app's code");
        assertThat(spec.inputs()).singleElement().isInstanceOfSatisfying(DialogSpec.TextInput.class, field -> {
            assertThat(field.key()).isEqualTo(AuthScreens.CODE);
            assertThat(plain(field.label())).isEqualTo("App code");
            assertThat(field.maxLength()).isEqualTo(AuthScreens.LONGEST_CODE);
        });
        assertThat(spec.buttons()).extracting(DialogSpec.Button::action).containsExactly("factor", "cancel");
        assertThat(spec.canClose()).isFalse();
        assertThat(screens(shipped(false), passwords(true)).code(steve, null).body())
                .extracting(AuthScreensTest::plain)
                .containsExactly("Type the app's code");
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
    @DisplayName("while a name can be claimed, the login screen says \"this name is mine\" between log in and leave")
    void theLoginScreenOffersTheClaim() {
        DialogSpec spec = new AuthScreens(
                        () -> withClaim(shipped(false)),
                        () -> passwords(true),
                        messages,
                        new Styler(Theme.defaults()),
                        () -> true)
                .login(steve, null);

        assertThat(spec.buttons()).extracting(DialogSpec.Button::action).containsExactly("login", "claim", "cancel");
    }

    @Test
    @DisplayName("where no name can be claimed, or the operator left the label empty, there is no claim button")
    void noClaimButtonWhereNothingCanBeClaimed() {
        DialogSpec cannot = new AuthScreens(
                        () -> withClaim(shipped(false)),
                        () -> passwords(true),
                        messages,
                        new Styler(Theme.defaults()),
                        () -> false)
                .login(steve, null);
        DialogSpec unlabelled = new AuthScreens(
                        () -> shipped(false), () -> passwords(true), messages, new Styler(Theme.defaults()), () -> true)
                .login(steve, null);

        assertThat(cannot.buttons()).extracting(DialogSpec.Button::action).containsExactly("login", "cancel");
        assertThat(unlabelled.buttons()).extracting(DialogSpec.Button::action).containsExactly("login", "cancel");
    }

    @Test
    @DisplayName("while sessions are by choice, login, register and the pad carry a \"remember me\" box")
    void theScreensAskToRemember() {
        AuthScreens screens = new AuthScreens(
                AuthScreensTest::withRemember,
                () -> passwords(false),
                messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> true);

        assertThat(screens.asksToRemember()).isTrue();
        assertThat(screens.login(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password", "remember");
        assertThat(screens.register(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password", "remember");
        assertThat(screens.pad(steve, null, 0, false, false).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("remember");
        assertThat(screens.login(steve, null).inputs().get(1)).isInstanceOf(DialogSpec.Toggle.class);
    }

    @Test
    @DisplayName("while sessions are not by choice, or the operator left the label empty, there is no box")
    void noBoxWhileSessionsAreNotChosen() {
        AuthScreens notChosen = new AuthScreens(
                AuthScreensTest::withRemember,
                () -> passwords(false),
                messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> false);
        AuthScreens unlabelled = new AuthScreens(
                () -> shipped(false),
                () -> passwords(false),
                messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> true);

        assertThat(notChosen.asksToRemember()).isFalse();
        assertThat(notChosen.login(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password");
        assertThat(notChosen.register(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password");
        assertThat(notChosen.pad(steve, null, 0, false, false).inputs()).isEmpty();
        assertThat(unlabelled.login(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password");
        assertThat(unlabelled.register(steve, null).inputs())
                .extracting(DialogSpec.Input::key)
                .containsExactly("password");
    }

    private static ScreenSettings withRemember() {
        ScreenSettings settings = shipped(false);
        return new ScreenSettings(
                settings.waitingRoom(),
                settings.cancel(),
                settings.canClose(),
                settings.pinPad(),
                settings.rules(),
                screen("register", "password", "confirm", "accept", "remember", "submit", "cancel"),
                screen("login", "password", "remember", "submit", "cancel"),
                screen("pin", "erase", "enter", "remember", "cancel"));
    }

    private static ScreenSettings withClaim(ScreenSettings settings) {
        return new ScreenSettings(
                settings.waitingRoom(),
                settings.cancel(),
                settings.canClose(),
                settings.pinPad(),
                settings.rules(),
                settings.register(),
                screen("login", "password", "submit", "claim", "cancel"),
                settings.pin());
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
