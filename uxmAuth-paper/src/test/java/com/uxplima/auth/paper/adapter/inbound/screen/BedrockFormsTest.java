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
        });
    }

    private AuthScreens screens() {
        return new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
    }

    /** Records the last form sent. */
    private static final class Forms implements BedrockScreen {
        int shown;
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
            this.widgets = widgets;
            this.submit = onSubmit;
            this.close = onClose;
        }
    }
}
