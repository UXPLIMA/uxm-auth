package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
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

/** A Bedrock player reads every screen as a native form, and the form answers the way a dialog does. */
final class BedrockFormsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private ScreenSettings settings;
    private final Forms forms = new Forms();
    private final List<String> answered = new ArrayList<>();
    private final List<@Nullable String> passwords = new ArrayList<>();
    private final List<Boolean> ticked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        settings = new ScreenFile(folder.resolve("screens.conf")).current();
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the login screen is a custom form with the password field, and its submit is the login")
    void theLoginScreenIsACustomForm() {
        bedrockForms().show(steve, screens().login(steve, null));

        assertThat(forms.widgets)
                .singleElement()
                .isInstanceOfSatisfying(
                        BedrockWidget.Input.class,
                        input -> assertThat(input.name()).isEqualTo(AuthScreens.PASSWORD));
        forms.submit.accept(Map.of(AuthScreens.PASSWORD, "hunter22"));

        assertThat(answered).containsExactly(AuthScreens.LOGIN);
        assertThat(passwords).containsExactly("hunter22");
    }

    @Test
    @DisplayName("\"forgot password\" stays off the Bedrock login form, which keeps its password field; a Bedrock"
            + " player types /recover, since the form shows in the world")
    void theLoginFormLeavesRecoveryToTheCommand() {
        AuthScreens recovering = new AuthScreens(
                () -> settings,
                () -> world.passwords,
                world.messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> false,
                () -> true);
        assertThat(recovering.login(steve, null).buttons())
                .extracting(DialogSpec.Button::action)
                .contains(AuthScreens.FORGOT);

        bedrockForms().show(steve, recovering.login(steve, null));

        assertThat(forms.widgets)
                .singleElement()
                .isInstanceOfSatisfying(
                        BedrockWidget.Input.class,
                        input -> assertThat(input.name()).isEqualTo(AuthScreens.PASSWORD));
        forms.submit.accept(Map.of(AuthScreens.PASSWORD, "hunter22"));
        assertThat(answered).containsExactly(AuthScreens.LOGIN);
    }

    @Test
    @DisplayName("while sessions are by choice, the login form has a \"remember me\" toggle and its tick comes back")
    void theLoginFormCarriesTheRememberToggle() {
        AuthScreens asking = new AuthScreens(
                () -> settings,
                () -> world.passwords,
                world.messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> true);
        bedrockForms().show(steve, asking.login(steve, null));

        assertThat(forms.widgets).hasSize(2);
        assertThat(forms.widgets.get(1))
                .isInstanceOfSatisfying(
                        BedrockWidget.Toggle.class,
                        toggle -> assertThat(toggle.name()).isEqualTo(AuthScreens.REMEMBER));
        forms.submit.accept(Map.of(AuthScreens.PASSWORD, "hunter22", AuthScreens.REMEMBER, "true"));

        assertThat(answered).containsExactly(AuthScreens.LOGIN);
        assertThat(ticked).containsExactly(true);
    }

    @Test
    @DisplayName("a Bedrock player reads the captcha in a custom form: the code in its text, one field, its submit"
            + " the answer")
    void theCaptchaIsACustomForm() {
        java.util.List<@Nullable String> codes = new ArrayList<>();
        BedrockForms answering = new BedrockForms(forms, (player, action, answer) -> {
            answered.add(action);
            codes.add(answer.text(AuthScreens.CODE));
        });

        answering.show(steve, screens().captcha(steve, "K7QXR", null));

        assertThat(forms.content).contains("K7QXR");
        assertThat(forms.widgets)
                .singleElement()
                .isInstanceOfSatisfying(
                        BedrockWidget.Input.class,
                        input -> assertThat(input.name()).isEqualTo(AuthScreens.CODE));
        forms.submit.accept(Map.of(AuthScreens.CODE, "k7qxr"));
        assertThat(answered).containsExactly(AuthScreens.CAPTCHA);
        assertThat(codes).containsExactly("k7qxr");
    }

    @Test
    @DisplayName("closing the form is leaving the screen")
    void closingIsCancel() {
        bedrockForms().show(steve, screens().login(steve, null));

        forms.close.run();

        assertThat(answered).containsExactly(AuthScreens.CANCEL);
    }

    @Test
    @DisplayName("the pad is a list of buttons, and a tap is that button's press")
    void thePadIsASimpleForm() {
        settings = new ScreenSettings(
                settings.waitingRoom(),
                settings.cancel(),
                settings.canClose(),
                true,
                settings.rules(),
                settings.register(),
                settings.login(),
                settings.pin());
        DialogSpec pad = screens().login(steve, null);
        bedrockForms().show(steve, pad);

        assertThat(forms.buttons).hasSameSizeAs(pad.buttons());
        forms.select.accept(4);

        assertThat(answered).containsExactly(pad.buttons().get(4).action());
    }

    @Test
    @DisplayName("a Bedrock player is shown the form and a Java player the dialog")
    void theRouterChoosesByClient() {
        List<Audience> java = new ArrayList<>();
        ScreenShower dialogs = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                java.add(who);
            }

            @Override
            public void close(Audience who) {}
        };
        PlayerMock alex = server.addPlayer("Alex");
        ScreenRouter router = new ScreenRouter(dialogs, bedrockForms(), id -> id.equals(steve.getUniqueId()));

        router.show(steve, screens().login(steve, null));
        router.show(alex, screens().login(alex, null));

        assertThat(forms.shown).isEqualTo(1);
        assertThat(java).containsExactly(alex);
    }

    private BedrockForms bedrockForms() {
        return new BedrockForms(forms, (player, action, answer) -> {
            answered.add(action);
            passwords.add(answer.text(AuthScreens.PASSWORD));
            ticked.add(answer.ticked(AuthScreens.REMEMBER));
        });
    }

    private AuthScreens screens() {
        return new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
    }

    /** Records the last form sent. */
    private static final class Forms implements BedrockScreen {
        int shown;

        @Nullable String content;

        List<BedrockWidget> widgets = List.of();
        List<BedrockButton> buttons = List.of();
        Consumer<Map<String, String>> submit = values -> {};
        Runnable close = () -> {};
        IntConsumer select = index -> {};

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            shown++;
            this.buttons = buttons;
            this.select = onSelect;
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {
            shown++;
            this.content = content;
            this.widgets = widgets;
            this.submit = onSubmit;
            this.close = onClose;
        }
    }
}
