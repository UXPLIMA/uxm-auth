package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import net.kyori.adventure.audience.Audience;

import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.testing.AuthWorld;
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

/** In the frozen world a player who can see a dialog is shown one; in dialog mode the world shows none. */
final class InWorldScreensTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private final List<Audience> shownTo = new ArrayList<>();
    private final List<DialogSpec> shown = new ArrayList<>();
    private final java.util.Set<java.util.UUID> sentToTheWorld = new java.util.HashSet<>();
    private boolean rules;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("frozen: a player with no account is shown the register screen, one with an account the login screen")
    void frozenShowsTheScreenForTheirCase() {
        PlayerMock newcomer = server.addPlayer("Newcomer");
        PlayerMock steve = server.addPlayer("Steve");

        InWorldScreens offer = offer(ScreenSettings.WaitingRoom.FROZEN);
        offer.offer(newcomer, new AuthState.Unregistered(new PlayerName("Newcomer")));
        world.registered(steve, "hunter22");
        offer.offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());

        assertThat(shown).hasSize(2);
        assertThat(shown.get(0).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.REGISTER);
        assertThat(shown.get(1).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.LOGIN);
    }

    @Test
    @DisplayName("a captcha that stands before the player's next step is shown before the screen of their case")
    void theCaptchaComesFirst() {
        PlayerMock newcomer = server.addPlayer("Newcomer");
        PlayerMock steve = server.addPlayer("Steve");
        world.captchaSettings = new com.uxplima.auth.application.protection.Captchas.Settings(
                com.uxplima.auth.application.protection.Captchas.When.REGISTER,
                3,
                5,
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                java.time.Duration.ofMinutes(60),
                com.uxplima.auth.application.protection.Captchas.JavaScreen.DIALOG);
        world.registered(steve, "hunter22");

        InWorldScreens offer = offer(ScreenSettings.WaitingRoom.FROZEN);
        offer.offer(newcomer, new AuthState.Unregistered(new PlayerName("Newcomer")));
        offer.offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());

        assertThat(shown).hasSize(2);
        assertThat(shown.get(0).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.CAPTCHA);
        assertThat(world.captchas.code(newcomer.getUniqueId())).isPresent();
        assertThat(shown.get(1).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.LOGIN);
    }

    @Test
    @DisplayName("a name that failed so often is shown the captcha before the login screen")
    void theCaptchaComesBeforeTheLoginAfterFailures() {
        PlayerMock steve = server.addPlayer("Steve");
        world.captchaSettings = new com.uxplima.auth.application.protection.Captchas.Settings(
                com.uxplima.auth.application.protection.Captchas.When.FAILURES,
                1,
                5,
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                java.time.Duration.ofMinutes(60),
                com.uxplima.auth.application.protection.Captchas.JavaScreen.DIALOG);
        world.registered(steve, "hunter22");
        world.captchas.failed(new PlayerName("Steve"));

        offer(ScreenSettings.WaitingRoom.FROZEN)
                .offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());

        assertThat(shown)
                .singleElement()
                .satisfies(spec -> assertThat(spec.buttons())
                        .extracting(DialogSpec.Button::action)
                        .contains(AuthScreens.CAPTCHA));
    }

    @Test
    @DisplayName("a player held to replace a weak password is shown the replace screen with why on top")
    void aHeldPlayerIsShownTheReplaceScreen() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        com.uxplima.auth.domain.account.Account account = ((AuthState.Registered)
                        world.sessions.state(steve.getUniqueId()).orElseThrow())
                .account();

        offer(ScreenSettings.WaitingRoom.FROZEN)
                .offer(
                        steve,
                        new AuthState.Replacing(
                                account, new com.uxplima.auth.domain.password.PasswordVerdict.Common()));

        assertThat(shown).singleElement().satisfies(spec -> {
            assertThat(spec.buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.REPLACE);
            assertThat(spec.inputs()).extracting(DialogSpec.Input::key).containsExactly("password", "confirm");
        });
    }

    @Test
    @DisplayName("a player who gave the password and waits for the app's code is shown the code screen")
    void aPlayerWaitingForTheCodeIsShownTheCodeScreen() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");
        com.uxplima.auth.domain.account.Account account = ((AuthState.Registered)
                        world.sessions.state(steve.getUniqueId()).orElseThrow())
                .account();

        offer(ScreenSettings.WaitingRoom.FROZEN)
                .offer(
                        steve,
                        new AuthState.SecondFactor(
                                account,
                                com.uxplima.auth.domain.state.AuthMethod.PASSWORD,
                                java.util.Set.of(com.uxplima.auth.domain.factor.FactorKind.APP),
                                com.uxplima.auth.domain.state.Attempts.fresh(3),
                                null));

        assertThat(shown).singleElement().satisfies(spec -> {
            assertThat(spec.buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.FACTOR);
            assertThat(spec.inputs()).extracting(DialogSpec.Input::key).containsExactly(AuthScreens.CODE);
        });
        assertThat(shownTo).containsExactly(steve);
    }

    @Test
    @DisplayName("dialog mode: a player the dialog sent to the world, too old for it or leaving it, is shown none")
    void dialogModeShowsNothingToThoseItSentToTheWorld() {
        PlayerMock steve = server.addPlayer("Steve");
        sentToTheWorld.add(steve.getUniqueId());

        offer(ScreenSettings.WaitingRoom.DIALOG).offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));

        assertThat(shown).isEmpty();
    }

    @Test
    @DisplayName("dialog mode: a player who logs out in the world is shown the screen there")
    void dialogModeShowsTheScreenAfterALogout() {
        PlayerMock steve = server.addPlayer("Steve");
        world.registered(steve, "hunter22");

        offer(ScreenSettings.WaitingRoom.DIALOG)
                .offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());

        assertThat(shown)
                .singleElement()
                .satisfies(spec -> assertThat(spec.buttons())
                        .extracting(DialogSpec.Button::action)
                        .contains(AuthScreens.LOGIN));
    }

    @Test
    @DisplayName("with the rules on, a player who must register reads them in chat, and how /register accepts them")
    void theRulesAreReadInChatBeforeRegistering() {
        rules = true;
        PlayerMock steve = server.addPlayer("Steve");

        offer(ScreenSettings.WaitingRoom.FROZEN).offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RULES_LINE_1));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.RULES_LINE_2));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.RULES_ACCEPTED_BY_REGISTERING));
    }

    @Test
    @DisplayName("a player with an account, or a server with the rules off, reads no rules")
    void noRulesForAnAccountOrWithTheRulesOff() {
        PlayerMock steve = server.addPlayer("Steve");
        offer(ScreenSettings.WaitingRoom.FROZEN).offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(steve.nextComponentMessage()).isNull();

        rules = true;
        world.registered(steve, "hunter22");
        offer(ScreenSettings.WaitingRoom.FROZEN)
                .offer(steve, world.sessions.state(steve.getUniqueId()).orElseThrow());
        assertThat(steve.nextComponentMessage()).isNull();
    }

    @Test
    @DisplayName("the screen is shown a moment after the join, and not at all to a player who got in meanwhile")
    void theScreenWaitsForTheClientToBeReady() {
        PlayerMock steve = server.addPlayer("Steve");
        List<Runnable> later = new ArrayList<>();
        java.util.Set<java.util.UUID> waiting = new java.util.HashSet<>(java.util.Set.of(steve.getUniqueId()));
        InWorldScreens offer = offer(
                ScreenSettings.WaitingRoom.FROZEN,
                (player, task) -> later.add(task),
                player -> waiting.contains(player.getUniqueId()));

        offer.offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));
        assertThat(shown).isEmpty();
        later.forEach(Runnable::run);
        assertThat(shown).hasSize(1);

        later.clear();
        offer.offer(steve, new AuthState.Unregistered(new PlayerName("Steve")));
        waiting.clear();
        later.forEach(Runnable::run);
        assertThat(shown).hasSize(1);
    }

    private InWorldScreens offer(ScreenSettings.WaitingRoom room) {
        return offer(room, (player, task) -> task.run(), player -> true);
    }

    private InWorldScreens offer(
            ScreenSettings.WaitingRoom room,
            java.util.function.BiConsumer<org.bukkit.entity.Player, Runnable> later,
            java.util.function.Predicate<org.bukkit.entity.Player> stillWaiting) {
        ScreenSettings shipped = new ScreenFile(folder.resolve("screens.conf")).current();
        ScreenSettings settings = new ScreenSettings(
                room,
                shipped.cancel(),
                shipped.canClose(),
                shipped.pinPad(),
                rules ? new ScreenSettings.Rules(true, shipped.rules().lines()) : shipped.rules(),
                shipped.register(),
                shipped.login(),
                shipped.pin());
        AuthScreens screens =
                new AuthScreens(() -> settings, () -> world.passwords, world.messages, new Styler(Theme.defaults()));
        ScreenShower shower = new ScreenShower() {
            @Override
            public void show(Audience who, DialogSpec spec) {
                shown.add(spec);
                shownTo.add(who);
            }

            @Override
            public void close(Audience who) {}
        };
        return new InWorldScreens(
                () -> settings,
                screens,
                sentToTheWorld::contains,
                later,
                stillWaiting,
                shower,
                new CaptchaScreen(world.captchas, screens, shower, world.messages, CaptchaScreen.Hand.NONE),
                new FactorScreen(world.confirmFactor, screens, shower, world.messages, world.lane));
    }
}
