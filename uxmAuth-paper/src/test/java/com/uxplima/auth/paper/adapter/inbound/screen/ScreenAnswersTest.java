package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ScreenSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.ClaimName;
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
        // The plugin file grants both to everyone; the test server has to be told so.
        server.getPluginManager().addPermission(new Permission("uxmauth.login", PermissionDefault.TRUE));
        server.getPluginManager().addPermission(new Permission("uxmauth.register", PermissionDefault.TRUE));
        server.getPluginManager().addPermission(new Permission("uxmauth.claim", PermissionDefault.TRUE));
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

        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    @DisplayName("a wrong password comes back on the login screen with the tries left on top")
    void aWrongPasswordShowsTheScreenAgain() {
        world.registered(steve, "hunter22");

        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "nope1234"))));

        assertThat(shown).hasSize(1);
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(
                        steve, Keys.LOGIN_WRONG, com.uxplima.uxmlib.text.Text.placeholder("left", "2"))));
    }

    @Test
    @DisplayName("a wrong password on the screen that bans the address sends the player away with the ban moment")
    void aBanOnTheScreenSendsAway() {
        world.banSteps = new com.uxplima.auth.domain.protection.BanSteps(
                List.of(java.time.Duration.ofMinutes(5)), 1, java.time.Duration.ofHours(24));
        world.registered(steve, "hunter22");

        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "nope1234"))));

        assertThat(world.fired).contains(AuthSettings.Moment.BANNED);
        assertThat(steve.isOnline()).isFalse();
        assertThat(shown).isEmpty();
    }

    @Test
    @DisplayName("a weak right password brings up the replace screen with why, and a new one there lets the player in")
    void aWeakPasswordIsReplacedOnTheScreen() {
        world.registered(steve, "hunter22");
        world.policy = new com.uxplima.auth.domain.password.PasswordPolicy(
                10, 64, java.util.regex.Pattern.compile("\\S+"), java.util.Set.of(), 0);

        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(shown).hasSize(1);
        assertThat(shown.get(0).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.REPLACE);
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(
                        steve, Keys.PASSWORD_TOO_SHORT, com.uxplima.uxmlib.text.Text.placeholder("min", "10"))));

        world.scheduler.settle(answers()
                .answer(
                        guest(),
                        AuthScreens.REPLACE,
                        answer(Map.of("password", "longenough1", "confirm", "other1234567"))));
        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(plain(shown.get(1).body().get(0)))
                .isEqualTo(plain(world.messages.render(steve, Keys.REGISTER_MISMATCH)));

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.REPLACE, answer(Map.of("password", "short12", "confirm", "short12"))));
        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(shown.get(2).buttons()).extracting(DialogSpec.Button::action).contains(AuthScreens.REPLACE);
        assertThat(plain(shown.get(2).body().get(0)))
                .isEqualTo(plain(world.messages.render(
                        steve, Keys.PASSWORD_TOO_SHORT, com.uxplima.uxmlib.text.Text.placeholder("min", "10"))));

        world.scheduler.settle(answers()
                .answer(
                        guest(),
                        AuthScreens.REPLACE,
                        answer(Map.of("password", "longenough1", "confirm", "longenough1"))));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(closed).isEqualTo(1);
    }

    @Test
    @DisplayName("a logged-in player cannot set a new password through the replace action, which asks no old one")
    void aLoggedInPlayerCannotReplace() {
        world.registered(steve, "hunter22");
        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));
        assertThat(world.isAuthenticated(steve)).isTrue();

        world.scheduler.settle(answers()
                .answer(
                        guest(),
                        AuthScreens.REPLACE,
                        answer(Map.of("password", "takenover1", "confirm", "takenover1"))));

        com.uxplima.auth.domain.hash.PasswordHash stored = java.util.Objects.requireNonNull(world.accounts
                .findByName(new com.uxplima.auth.domain.name.PlayerName("Steve"))
                .orElseThrow()
                .password());
        assertThat(world.hashing.verify(com.uxplima.auth.domain.password.Password.of("hunter22"), stored))
                .isNotInstanceOf(com.uxplima.auth.domain.hash.Verification.Wrong.class);
    }

    @Test
    @DisplayName("a registration with both passwords makes the account")
    void aRegisterAnswerMakesTheAccount() {
        world.unregistered(steve);

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.REGISTERED);
    }

    @Test
    @DisplayName("an address past the accounts limit sees the register screen again with the reason on top")
    void aFullAddressSeesTheReason() {
        world.accountSettings = new com.uxplima.auth.application.protection.AccountCap.Settings(
                1, java.time.Duration.ofHours(24), new com.uxplima.auth.domain.protection.AddressRanges(List.of()));
        world.accounts.insert(new com.uxplima.auth.domain.account.Account(
                java.util.UUID.randomUUID(),
                new com.uxplima.auth.domain.name.PlayerName("Alex"),
                com.uxplima.auth.domain.account.AccountKind.CRACKED,
                null,
                world.hashing.hash(com.uxplima.auth.domain.password.Password.of("secret99")),
                world.clock.instant(),
                steve.getAddress().getAddress().getHostAddress(),
                null,
                null));
        world.unregistered(steve);

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(steve, Keys.REGISTER_TOO_MANY_ACCOUNTS)));
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
                .answer(guest(), AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(plain(shown.get(0).body().get(0)))
                .isEqualTo(plain(world.messages.render(steve, Keys.REGISTER_RULES_NEEDED)));
    }

    @Test
    @DisplayName("leaving the screen kicks, or lets the player into the frozen world, as screens.conf says")
    void cancelFollowsTheFile() {
        world.registered(steve, "hunter22");
        settings = withCancel(ScreenSettings.Cancel.WORLD);

        world.scheduler.settle(answers().answer(guest(), AuthScreens.CANCEL, answer(Map.of())));
        assertThat(steve.isOnline()).isTrue();
        assertThat(closed).isEqualTo(1);

        settings = withCancel(ScreenSettings.Cancel.KICK);
        world.scheduler.settle(answers().answer(guest(), AuthScreens.CANCEL, answer(Map.of())));
        assertThat(steve.isOnline()).isFalse();
    }

    @Test
    @DisplayName("\"this name is mine\" on the login screen sends the player away and asks their next connection")
    void theClaimButtonSendsThePlayerAway() {
        world.registered(steve, "hunter22");
        String here = java.util.Objects.requireNonNull(steve.getAddress())
                .getAddress()
                .getHostAddress();

        world.scheduler.settle(answers().answer(guest(), "claim", answer(Map.of())));

        assertThat(steve.isOnline()).isFalse();
        assertThat(claimed).containsExactly(steve.getUniqueId() + "@" + here);
    }

    @Test
    @DisplayName("a claim the rule does not take shows the login screen again with the reason on top")
    void aClaimTheRuleRefusesShowsTheReason() {
        world.registered(steve, "hunter22");
        claimResult = ClaimName.Result.NOT_THIS_RULE;

        world.scheduler.settle(answers().answer(guest(), "claim", answer(Map.of())));

        assertThat(steve.isOnline()).isTrue();
        assertThat(plain(shown.getLast().body().getFirst())).contains("nothing to claim");
    }

    @Test
    @DisplayName("a claim press where the login screen offers no claim is thrown away, whatever the client sends")
    void aClaimPressWithoutTheButtonIsThrownAway() {
        world.registered(steve, "hunter22");
        claimable = false;

        world.scheduler.settle(answers().answer(guest(), "claim", answer(Map.of())));

        assertThat(steve.isOnline()).isTrue();
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("a player denied the claim command cannot claim through the screen either")
    void theClaimButtonAsksTheCommandsPermission() {
        world.registered(steve, "hunter22");
        steve.addAttachment(MockBukkit.createMockPlugin(), "uxmauth.claim", false);

        world.scheduler.settle(answers().answer(guest(), "claim", answer(Map.of())));

        assertThat(steve.isOnline()).isTrue();
        assertThat(claimed).isEmpty();
    }

    @Test
    @DisplayName("a player denied the login command cannot log in through the screen either")
    void theLoginScreenAsksTheCommandsPermission() {
        world.registered(steve, "hunter22");
        steve.addAttachment(MockBukkit.createMockPlugin("Perms"), "uxmauth.login", false);

        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a player denied the login command cannot replace a weak password through the screen either")
    void theReplaceScreenAsksTheLoginPermission() {
        world.registered(steve, "hunter22");
        world.policy = new com.uxplima.auth.domain.password.PasswordPolicy(
                10, 64, java.util.regex.Pattern.compile("\\S+"), java.util.Set.of(), 0);
        world.scheduler.settle(answers().answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"))));
        steve.addAttachment(MockBukkit.createMockPlugin("Perms"), "uxmauth.login", false);

        world.scheduler.settle(answers()
                .answer(
                        guest(),
                        AuthScreens.REPLACE,
                        answer(Map.of("password", "longenough1", "confirm", "longenough1"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a player denied the register command cannot register through the screen either")
    void theRegisterScreenAsksTheCommandsPermission() {
        world.unregistered(steve);
        steve.addAttachment(MockBukkit.createMockPlugin("Perms"), "uxmauth.register", false);

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a ticked \"remember me\" box stores the choice and remembers the login from this address")
    void aTickedBoxRemembers() {
        world.registered(steve, "hunter22");
        asksToRemember = true;

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"), AuthScreens.REMEMBER)));
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.remember.chose(steve.getUniqueId())).isTrue();
        assertThat(world.remembered.holds(steve.getUniqueId(), address(), world.clock.instant()))
                .isTrue();
    }

    @Test
    @DisplayName("an unticked box, a wrong password, or a box the screen never offered, remember nobody")
    void onlyAnOfferedTickedBoxOnARightPasswordRemembers() {
        world.registered(steve, "hunter22");
        asksToRemember = true;
        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "nope1234"), AuthScreens.REMEMBER)));
        world.scheduler.drain();
        assertThat(world.remember.chose(steve.getUniqueId())).isFalse();

        asksToRemember = false;
        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.LOGIN, answer(Map.of("password", "hunter22"), AuthScreens.REMEMBER)));
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.remember.chose(steve.getUniqueId())).isFalse();
        assertThat(world.remembered.holds(steve.getUniqueId(), address(), world.clock.instant()))
                .isFalse();
    }

    @Test
    @DisplayName("an unticked box on a registration stores no choice")
    void anUntickedRegistrationRemembersNobody() {
        world.unregistered(steve);
        asksToRemember = true;

        world.scheduler.settle(answers()
                .answer(guest(), AuthScreens.REGISTER, answer(Map.of("password", "hunter22", "confirm", "hunter22"))));
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.remember.chose(steve.getUniqueId())).isFalse();
    }

    private String address() {
        return steve.getAddress().getAddress().getHostAddress();
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

    private final List<String> claimed = new java.util.ArrayList<>();
    private ClaimName.Result claimResult = ClaimName.Result.ASKED;
    private boolean claimable = true;
    private boolean asksToRemember;

    private ScreenAnswers answers() {
        AuthScreens screens = new AuthScreens(
                () -> settings,
                () -> world.passwords,
                world.messages,
                new Styler(Theme.defaults()),
                () -> claimable,
                () -> asksToRemember);
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
                () -> settings,
                () -> world.passwords,
                world.messages,
                shower,
                id -> world.sessions.state(id).orElse(null) instanceof AuthState.Unregistered,
                (id, address) -> {
                    claimed.add(id + "@" + address);
                    return claimResult;
                },
                world.sessions::chose,
                world.change);
    }

    private Guest guest() {
        return new PlayerGuest(steve, world.openingOf(steve), world.door, world.moments, world.replies);
    }

    private static ScreenAnswers.Answer answer(Map<String, String> texts, String... ticked) {
        List<String> boxes = List.of(ticked);
        return new ScreenAnswers.Answer() {
            @Override
            public @Nullable String text(String key) {
                return texts.get(key);
            }

            @Override
            public boolean ticked(String key) {
                return boxes.contains(key);
            }
        };
    }

    private static String plain(net.kyori.adventure.text.Component line) {
        return PlainTextComponentSerializer.plainText().serialize(line);
    }
}
