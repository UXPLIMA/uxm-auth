package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
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

/**
 * "Forgot password" on the login screen: a player in the dialog before the world, who cannot type a command, asks a
 * code by mail and sets a new password with it on a screen of its own.
 */
final class ScreenRecoveryTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private ScreenSettings settings;
    private final List<DialogSpec> shown = new ArrayList<>();
    private boolean recoverable = true;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        server.getPluginManager().addPermission(new Permission("uxmauth.login", PermissionDefault.TRUE));
        server.getPluginManager().addPermission(new Permission("uxmauth.recover", PermissionDefault.TRUE));
        steve = server.addPlayer("Steve");
        settings = new ScreenFile(folder.resolve("screens.conf")).current();
        world.registered(steve, "hunter22");
        world.emails.setAddress(
                world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id(),
                EmailAddress.parse("steve@example.net").orElseThrow());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("\"forgot password\" mails a code and shows the recovery screen, saying where the code went")
    void forgotMailsACode() {
        press(AuthScreens.FORGOT, Map.of());

        assertThat(world.mailed).hasSize(1);
        assertThat(actions(last())).containsExactly(AuthScreens.RECOVER, AuthScreens.BACK);
        assertThat(firstLine(last()))
                .isEqualTo(plain(world.messages.render(
                        steve,
                        Keys.RECOVER_SENT,
                        Text.placeholder("address", "s***@e***.net"),
                        Text.placeholder("minutes", "15"))));
    }

    @Test
    @DisplayName("the code and a new password set it; the login screen says so, and the new password logs in")
    void theCodeSetsANewPassword() {
        press(AuthScreens.FORGOT, Map.of());

        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, mailedCode(), AuthScreens.PASSWORD, "fresh7777"));

        assertThat(actions(last())).contains(AuthScreens.LOGIN);
        assertThat(firstLine(last())).isEqualTo(plain(world.messages.render(steve, Keys.RECOVER_DONE)));
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("fresh7777"), "127.0.0.1")
                        .join())
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    @Test
    @DisplayName("a wrong code, or a new password the rules refuse, stays on the recovery screen and says why")
    void aWrongAnswerStays() {
        press(AuthScreens.FORGOT, Map.of());

        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, "000000", AuthScreens.PASSWORD, "fresh7777"));
        assertThat(actions(last())).containsExactly(AuthScreens.RECOVER, AuthScreens.BACK);
        assertThat(firstLine(last()))
                .isEqualTo(plain(world.messages.render(steve, Keys.RECOVER_WRONG, Text.placeholder("left", "4"))));

        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, mailedCode(), AuthScreens.PASSWORD, "abc"));
        assertThat(actions(last())).containsExactly(AuthScreens.RECOVER, AuthScreens.BACK);
        assertThat(world.sessions.state(steve.getUniqueId())).containsInstanceOf(AuthState.Registered.class);
    }

    @Test
    @DisplayName("a code without a new password says how to fill the screen, and spends no try of the code")
    void halfFilledSaysHow() {
        press(AuthScreens.FORGOT, Map.of());

        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, mailedCode()));

        assertThat(actions(last())).containsExactly(AuthScreens.RECOVER, AuthScreens.BACK);
        assertThat(firstLine(last())).isEqualTo(plain(world.messages.render(steve, Keys.RECOVER_USAGE)));
        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, "000000", AuthScreens.PASSWORD, "fresh7777"));
        assertThat(firstLine(last()))
                .as("the half-filled try spent nothing")
                .isEqualTo(plain(world.messages.render(steve, Keys.RECOVER_WRONG, Text.placeholder("left", "4"))));
    }

    @Test
    @DisplayName("back returns to the login screen; an account with no address is told so there, and nothing is mailed")
    void backAndNoAddress() {
        press(AuthScreens.BACK, Map.of());
        assertThat(actions(last())).contains(AuthScreens.LOGIN);

        world.emails.removeAddress(
                world.accounts.findByName(new PlayerName("Steve")).orElseThrow().id());
        press(AuthScreens.FORGOT, Map.of());

        assertThat(world.mailed).isEmpty();
        assertThat(actions(last())).contains(AuthScreens.LOGIN);
        assertThat(firstLine(last())).isEqualTo(plain(world.messages.render(steve, Keys.RECOVER_NO_ADDRESS)));
    }

    @Test
    @DisplayName("while the screen offers no recovery, a press a client sends anyway does nothing")
    void notOfferedDoesNothing() {
        recoverable = false;

        press(AuthScreens.FORGOT, Map.of());
        press(AuthScreens.RECOVER, Map.of(AuthScreens.CODE, "000000", AuthScreens.PASSWORD, "fresh7777"));

        assertThat(world.mailed).isEmpty();
        assertThat(shown).isEmpty();
    }

    private void press(String action, Map<String, String> texts) {
        world.scheduler.settle(answers().answer(guest(), action, answer(texts)));
    }

    private String mailedCode() {
        return Objects.requireNonNull(world.mailed.getLast().get("code"), "code");
    }

    private DialogSpec last() {
        assertThat(shown).isNotEmpty();
        return shown.getLast();
    }

    private static List<String> actions(DialogSpec spec) {
        return spec.buttons().stream().map(DialogSpec.Button::action).toList();
    }

    private static String firstLine(DialogSpec spec) {
        return plain(spec.body().getFirst());
    }

    private ScreenAnswers answers() {
        AuthScreens screens = new AuthScreens(
                () -> settings,
                () -> world.passwords,
                world.messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> false,
                () -> recoverable);
        ScreenShower shower = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
            }

            @Override
            public void close(Audience who) {}
        };
        return new ScreenAnswers(
                screens,
                world.register,
                world.logIn,
                () -> settings,
                () -> world.passwords,
                world.messages,
                shower,
                id -> world.sessions.state(id).orElse(null) instanceof AuthState.Unregistered,
                (id, address) -> com.uxplima.auth.application.premium.ClaimName.Result.NOT_THIS_RULE,
                world.sessions::chose,
                world.change,
                new CaptchaScreen(world.captchas, screens, shower, world.messages, CaptchaScreen.Hand.NONE),
                FactorOffer.NONE,
                new ScreenRecovery(world.recoverPassword(), world.sessions, screens, shower, world.messages));
    }

    private Guest guest() {
        return new PlayerGuest(steve, world.openingOf(steve), world.door, world.moments, world.replies);
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

    private static String plain(Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }
}
