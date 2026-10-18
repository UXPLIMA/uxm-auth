package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The code screen of the second step: the app's code lets in, a wrong one comes back with the tries left. */
final class FactorScreenTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;
    private final List<DialogSpec> shown = new ArrayList<>();
    private int closed;
    private FactorScreen screen;
    private TotpSecret secret;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
        ScreenSettings settings = new ScreenFile(folder.resolve("screens.conf")).current();
        AuthScreens screens = new AuthScreens(
                () -> settings,
                () -> world.passwords,
                world.messages,
                new Styler(Theme.defaults()),
                () -> false,
                () -> false);
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
        screen = new FactorScreen(world.confirmFactor, screens, shower, world.messages, world.lane);
        world.registered(steve, "hunter22");
        secret = world.appOn(steve);
        assertThat(world.logIn
                        .run(steve.getUniqueId(), Password.of("hunter22"), address())
                        .join())
                .isEqualTo(new LoginResult.CodeNeeded());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
    }

    private Guest guest() {
        return new PlayerGuest(steve, world.openingOf(steve), world.door, world.moments, world.replies);
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("the offer shows the code screen with the reason on top")
    void theOfferShowsTheScreen() {
        screen.offer(steve, world.messages.render(steve, Keys.FACTOR_CODE_NEEDED));

        assertThat(shown).singleElement().satisfies(spec -> {
            assertThat(spec.buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.FACTOR);
            assertThat(plain(spec.body().getFirst()))
                    .isEqualTo(plain(world.messages.render(steve, Keys.FACTOR_CODE_NEEDED)));
        });
    }

    @Test
    @DisplayName("the app's code lets the player in, with the logged-in moment, and closes the screen")
    void theCodeLetsIn() {
        world.scheduler.settle(screen.answer(guest(), world.codeNow(secret)));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong code comes back on the code screen with the tries left, and the wrong-password moment")
    void aWrongCodeComesBack() {
        world.scheduler.settle(screen.answer(guest(), "000000"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.WRONG_PASSWORD);
        assertThat(shown)
                .singleElement()
                .satisfies(spec -> assertThat(plain(spec.body().getFirst()))
                        .isEqualTo(plain(
                                world.messages.render(steve, Keys.FACTOR_LOGIN_WRONG, Text.placeholder("left", "2")))));
    }

    @Test
    @DisplayName("the last wrong code sends the player away")
    void theLastWrongCodeSendsAway() {
        for (int i = 0; i < AuthWorld.MAX_ATTEMPTS; i++) {
            world.scheduler.settle(screen.answer(guest(), "000000"));
        }

        assertThat(steve.isOnline()).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
    }

    @Test
    @DisplayName("a ban earned by a wrong code sends the player away with the ban moment")
    void aBanSendsAway() {
        world.banSteps = new com.uxplima.auth.domain.protection.BanSteps(
                List.of(Duration.ofMinutes(5)), 1, Duration.ofHours(24));

        world.scheduler.settle(screen.answer(guest(), "000000"));

        assertThat(steve.isOnline()).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.BANNED);
    }

    @Test
    @DisplayName("a weak password that waited behind the code brings up the replace screen once the code passes")
    void aWeakPasswordIsReplacedAfter() {
        PlayerMock alex = server.addPlayer("Alex");
        world.registered(alex, "hunter22");
        TotpSecret alexSecret = world.appOn(alex);
        world.policy = new com.uxplima.auth.domain.password.PasswordPolicy(
                10, 64, java.util.regex.Pattern.compile("\\S+"), java.util.Set.of(), 0);
        world.logIn.run(alex.getUniqueId(), Password.of("hunter22"), address()).join();

        world.scheduler.settle(screen.answer(
                new PlayerGuest(alex, world.openingOf(alex), world.door, world.moments, world.replies),
                world.codeNow(alexSecret)));

        assertThat(world.sessions.state(alex.getUniqueId())).containsInstanceOf(AuthState.Replacing.class);
        assertThat(shown)
                .singleElement()
                .satisfies(spec -> assertThat(spec.buttons())
                        .extracting(DialogSpec.Button::action)
                        .contains(AuthScreens.REPLACE));
    }

    @Test
    @DisplayName("a code from a player no code is asked of closes the screen and changes nothing")
    void nothingIsAsked() {
        world.scheduler.settle(screen.answer(guest(), world.codeNow(secret)));
        int before = closed;

        world.scheduler.settle(screen.answer(guest(), world.codeNow(secret)));

        assertThat(closed).isEqualTo(before + 1);
        assertThat(world.isAuthenticated(steve)).isTrue();
    }
}
