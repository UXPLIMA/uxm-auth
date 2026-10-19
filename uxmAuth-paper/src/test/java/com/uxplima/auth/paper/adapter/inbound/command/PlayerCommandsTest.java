package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;

import org.bukkit.potion.PotionEffectType;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.paper.testing.AuthWorld;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** The four commands a player types to get in, from the typed line to what the player sees. */
final class PlayerCommandsTest {

    @TempDir
    Path folder;

    private ServerMock server;
    private AuthWorld world;
    private PlayerMock steve;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = new AuthWorld(MockBukkit.createMockPlugin(), folder, AuthWorld.shippedPasswords());
        steve = server.addPlayer("Steve");
    }

    @AfterEach
    void tearDown() {
        world.close();
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("the right password lets the player in, gives back what the hold took and runs the login moment")
    void loginLetsThePlayerIn() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isFalse();
    }

    @Test
    @DisplayName("a wrong password says how many tries are left, and the last one kicks")
    void aWrongPasswordCountsDownToAKick() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();
        drain();

        world.scheduler.settle(login().attempt(steve, "wrong123"));
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.LOGIN_WRONG, Text.placeholder("left", "2")));
        assertThat(world.fired).contains(AuthSettings.Moment.WRONG_PASSWORD);

        world.scheduler.settle(login().attempt(steve, "wrong123"));
        world.scheduler.settle(login().attempt(steve, "wrong123"));

        assertThat(world.fired).contains(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
        assertThat(steve.isOnline()).isFalse();
    }

    @Test
    @DisplayName("a wrong password that bans the address kicks at once, runs the ban moment, and the door refuses it")
    void aBanKicksAndTheDoorRefuses() {
        world.banSteps = new com.uxplima.auth.domain.protection.BanSteps(
                java.util.List.of(java.time.Duration.ofMinutes(5)), 2, java.time.Duration.ofHours(24));
        world.registered(steve, "hunter22");
        String address = steve.getAddress().getAddress().getHostAddress();
        world.door.hold(steve);
        world.scheduler.drain();
        drain();

        world.scheduler.settle(login().attempt(steve, "wrong123"));
        assertThat(steve.isOnline()).isTrue();
        world.scheduler.settle(login().attempt(steve, "wrong123"));

        assertThat(world.fired)
                .contains(AuthSettings.Moment.BANNED)
                .doesNotContain(AuthSettings.Moment.TOO_MANY_ATTEMPTS);
        assertThat(steve.isOnline()).isFalse();
        assertThat(world.bans
                        .rule()
                        .check(new com.uxplima.auth.application.protection.Admission.Arrival(
                                address, "play.example.org")))
                .hasValueSatisfying(refused -> assertThat(refused.lasts()).isEqualTo(java.time.Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("two players behind one shared-address host: one's wrong passwords ban neither of them")
    void aSharedHostBansNobody() {
        world.sharedHosts = java.util.List.of("*.tcpshield.com");
        world.sharedSources = java.util.List.of(steve.getAddress().getAddress().getHostAddress());
        world.banSteps = new com.uxplima.auth.domain.protection.BanSteps(
                java.util.List.of(java.time.Duration.ofMinutes(5)), 2, java.time.Duration.ofHours(24));
        PlayerMock alex = server.addPlayer("Alex");
        String steveAddress = steve.getAddress().getAddress().getHostAddress();
        world.registered(steve, "hunter22");
        world.registered(alex, "secret99");
        for (PlayerMock player : java.util.List.of(steve, alex)) {
            world.sessions.open(
                    player.getUniqueId(),
                    world.sessions.state(player.getUniqueId()).orElseThrow(),
                    "visit-" + player.getName(),
                    "mc.tcpshield.com");
        }

        for (int i = 0; i < 3; i++) {
            world.scheduler.settle(login().attempt(steve, "wrong123"));
        }
        world.scheduler.settle(login().attempt(alex, "secret99"));

        assertThat(world.fired).doesNotContain(AuthSettings.Moment.BANNED);
        assertThat(world.isAuthenticated(alex)).isTrue();
        assertThat(world.bans
                        .rule()
                        .check(new com.uxplima.auth.application.protection.Admission.Arrival(
                                steveAddress, "play.example.org")))
                .isEmpty();
    }

    @Test
    @DisplayName("a right password today's rules refuse holds the player, says why, and a new one lets them in")
    void aWeakPasswordIsReplacedAtLogin() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();
        drain();
        world.policy = new com.uxplima.auth.domain.password.PasswordPolicy(
                10, 64, java.util.regex.Pattern.compile("\\S+"), java.util.Set.of(), 0);

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.PASSWORD_TOO_SHORT, Text.placeholder("min", "10")));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGIN_MUST_REPLACE));
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();

        world.scheduler.settle(changePassword(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "longenough1"));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_IN);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isFalse();
    }

    @Test
    @DisplayName("a registration with the password twice makes the account and lets the player in")
    void registerMakesTheAccount() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isTrue();
        assertThat(world.accounts.findByName(new PlayerName("steve"))).isPresent();
        assertThat(world.fired).contains(AuthSettings.Moment.REGISTERED);
    }

    @Test
    @DisplayName("an address past the accounts limit is told so, and no account is made")
    void aFullAddressCannotRegister() {
        world.accountSettings = new com.uxplima.auth.application.protection.AccountCap.Settings(
                1,
                java.time.Duration.ofHours(24),
                new com.uxplima.auth.domain.protection.AddressRanges(java.util.List.of()));
        world.accounts.insert(new com.uxplima.auth.domain.account.Account(
                java.util.UUID.randomUUID(),
                new PlayerName("Alex"),
                com.uxplima.auth.domain.account.AccountKind.CRACKED,
                null,
                world.hashing.hash(com.uxplima.auth.domain.password.Password.of("secret99")),
                world.clock.instant(),
                steve.getAddress().getAddress().getHostAddress(),
                null,
                null));
        world.unregistered(steve);
        drain();

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(world.accounts.findByName(new PlayerName("Steve"))).isEmpty();
        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.REGISTER_TOO_MANY_ACCOUNTS));
    }

    @Test
    @DisplayName("/register alone reaches the command and says how to register, once or twice as the server asks")
    void aBareRegisterSaysHow() {
        world.unregistered(steve);
        AuthSettings.Passwords once = new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), false, true, 3);

        assertThat(node(register(AuthWorld.shippedPasswords())).getCommand())
                .as("the bare label runs, rather than the game's own incomplete-command line")
                .isNotNull();
        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "", ""));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.REGISTER_USAGE));
        world.scheduler.settle(register(once).attempt(steve, "", ""));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.REGISTER_USAGE_ONCE));
        assertThat(world.accounts.findByName(new PlayerName("Steve"))).isEmpty();
    }

    @Test
    @DisplayName("/login alone reaches the command, says how to log in and spends no try")
    void aBareLoginSaysHow() {
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();

        assertThat(node(login()).getCommand()).isNotNull();
        // Three bare lines against three tries: a bare line that spent one would kick before the password.
        for (int bare = 0; bare < 3; bare++) {
            world.scheduler.settle(login().attempt(steve, ""));
            assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGIN_USAGE));
        }
        world.scheduler.settle(login().attempt(steve, "hunter22"));
        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("while the confirmation is asked for, one password is not enough")
    void registerAsksForTheConfirmation() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", ""));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.REGISTER_CONFIRM_NEEDED));
        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("with the confirmation off, one password makes the account")
    void registerWithoutTheConfirmation() {
        world.unregistered(steve);
        AuthSettings.Passwords once = new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), false, true, 3);

        world.scheduler.settle(register(once).attempt(steve, "hunter22", ""));

        assertThat(world.isAuthenticated(steve)).isTrue();
    }

    @Test
    @DisplayName("a refused password is told with its reason")
    void aRefusedPasswordIsExplained() {
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "abc", "abc"));

        assertThat(steve.nextComponentMessage())
                .isEqualTo(world.messages.render(steve, Keys.PASSWORD_TOO_SHORT, Text.placeholder("min", "6")));
    }

    @Test
    @DisplayName("a change needs the old password while old-on-change is on")
    void changeNeedsBothWhileAsked() {
        loggedIn("hunter22");

        world.scheduler.settle(changePassword(AuthWorld.shippedPasswords()).attempt(steve, "fresh777", ""));
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CHANGE_NEEDS_BOTH));

        world.scheduler.settle(changePassword(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "fresh777"));
        assertThat(world.fired).contains(AuthSettings.Moment.PASSWORD_CHANGED);
    }

    @Test
    @DisplayName("a logout puts the player back at the door")
    void logoutHoldsThePlayerAgain() {
        loggedIn("hunter22");

        logout().attempt(steve);
        world.scheduler.drain();

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(world.fired).contains(AuthSettings.Moment.LOGGED_OUT, AuthSettings.Moment.JOIN_REGISTERED);
        assertThat(steve.hasPotionEffect(PotionEffectType.BLINDNESS)).isTrue();
    }

    @Test
    @DisplayName("a logout of a player who is not logged in says so")
    void logoutWithoutALogin() {
        world.registered(steve, "hunter22");

        logout().attempt(steve);

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.LOGOUT_NOT_LOGGED_IN));
    }

    @Test
    @DisplayName("a right password typed after another connection opened the session again opens nothing")
    void aLoginForAReplacedOpeningOpensNothing() {
        world.registered(steve, "hunter22");
        world.bindOpening(steve);
        world.sessions.open(
                steve.getUniqueId(), world.sessions.state(steve.getUniqueId()).orElseThrow());

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a registration typed after another connection opened the session again makes nothing")
    void aRegistrationForAReplacedOpeningMakesNothing() {
        world.unregistered(steve);
        world.bindOpening(steve);
        world.sessions.open(
                steve.getUniqueId(), world.sessions.state(steve.getUniqueId()).orElseThrow());

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
    }

    @Test
    @DisplayName("a player whose account has no password is sent away to join again, not held at a door")
    void aLogoutWithNoPasswordSendsThePlayerAway() {
        world.sessions.open(
                steve.getUniqueId(),
                new com.uxplima.auth.application.LetBedrockIn(world.accounts, () -> AuthWorld.MAX_ATTEMPTS, world.clock)
                        .admit(
                                steve.getUniqueId(),
                                new com.uxplima.auth.domain.name.PlayerName("Steve"),
                                false,
                                "10.0.0.7"));

        logout().attempt(steve);
        drain();

        assertThat(steve.isOnline()).isFalse();
    }

    private void loggedIn(String password) {
        world.registered(steve, password);
        world.scheduler.settle(login().attempt(steve, password));
        assertThat(world.isAuthenticated(steve)).isTrue();
        drain();
    }

    @Test
    @DisplayName("a login the captcha stands before says so and shows the captcha, and spends no try")
    void aLoginWaitsForTheCaptcha() {
        world.captchaSettings = captcha(com.uxplima.auth.application.protection.Captchas.When.FAILURES);
        world.registered(steve, "hunter22");
        world.door.hold(steve);
        world.scheduler.drain();
        world.scheduler.settle(login().attempt(steve, "wrong123"));
        drain();
        world.fired.clear();

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(offered).containsExactly("Steve:" + plain(world.messages.render(steve, Keys.CAPTCHA_NEEDED)));
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.WRONG_PASSWORD, AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a right password on an account with the app says to type the code and shows the code screen")
    void aLoginAsksTheCodeNext() {
        world.registered(steve, "hunter22");
        world.appOn(steve);
        world.door.hold(steve);
        world.scheduler.drain();
        drain();
        world.fired.clear();

        world.scheduler.settle(login().attempt(steve, "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.FACTOR_CODE_NEEDED));
        assertThat(offered).containsExactly("Steve:code");
        assertThat(world.fired).doesNotContain(AuthSettings.Moment.LOGGED_IN);
    }

    @Test
    @DisplayName("a registration the captcha stands before says so and shows the captcha")
    void aRegistrationWaitsForTheCaptcha() {
        world.captchaSettings = captcha(com.uxplima.auth.application.protection.Captchas.When.REGISTER);
        world.unregistered(steve);

        world.scheduler.settle(register(AuthWorld.shippedPasswords()).attempt(steve, "hunter22", "hunter22"));

        assertThat(world.isAuthenticated(steve)).isFalse();
        assertThat(offered).containsExactly("Steve:" + plain(world.messages.render(steve, Keys.CAPTCHA_NEEDED)));
    }

    @Test
    @DisplayName("/captcha with no code waiting says so, a wrong code shows a new one, the right one the next screen")
    void theCaptchaCommandAnswers() {
        world.captchaSettings = captcha(com.uxplima.auth.application.protection.Captchas.When.REGISTER);
        world.unregistered(steve);
        java.util.List<String> handed = new java.util.ArrayList<>();
        java.util.List<com.uxplima.auth.domain.state.AuthState> next = new java.util.ArrayList<>();
        CaptchaCommand command = captchaCommand((player, code) -> handed.add(code), (player, state) -> next.add(state));
        drain();

        command.answer(steve, "ABCDE");
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CAPTCHA_NOTHING));

        com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen screen = captchaScreen((player, code) -> {
            handed.add(code);
            return true;
        });
        screen.offer(steve, steve.getUniqueId(), null);
        drain();
        command.answer(steve, "WRONG");
        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CAPTCHA_WRONG));
        assertThat(handed).hasSize(2).doesNotHaveDuplicates();
        drain();

        command.answer(steve, handed.getLast().toLowerCase(java.util.Locale.ROOT));

        assertThat(steve.nextComponentMessage()).isEqualTo(world.messages.render(steve, Keys.CAPTCHA_SOLVED));
        assertThat(next).singleElement().isInstanceOf(com.uxplima.auth.domain.state.AuthState.Unregistered.class);
        assertThat(world.captchas.blocksRegistration(steve.getUniqueId())).isFalse();
    }

    private final java.util.List<String> offered = new java.util.ArrayList<>();

    private static com.uxplima.auth.application.protection.Captchas.Settings captcha(
            com.uxplima.auth.application.protection.Captchas.When when) {
        return new com.uxplima.auth.application.protection.Captchas.Settings(
                when,
                1,
                5,
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                java.time.Duration.ofMinutes(60),
                com.uxplima.auth.application.protection.Captchas.JavaScreen.MAP);
    }

    private static String plain(net.kyori.adventure.text.Component line) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(line);
    }

    private com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen captchaScreen(
            com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen.Hand hand) {
        com.uxplima.auth.adapter.outbound.ScreenSettings screens =
                new com.uxplima.auth.adapter.outbound.ScreenFile(folder.resolve("screens.conf")).current();
        return new com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen(
                world.captchas,
                new com.uxplima.auth.paper.adapter.inbound.screen.AuthScreens(
                        () -> screens,
                        () -> world.passwords,
                        world.messages,
                        new com.uxplima.uxmlib.text.style.Styler(com.uxplima.uxmlib.text.style.Theme.defaults())),
                new com.uxplima.auth.paper.adapter.inbound.screen.ScreenShower() {
                    @Override
                    public void show(
                            net.kyori.adventure.audience.Audience who,
                            com.uxplima.auth.paper.adapter.inbound.screen.DialogSpec spec) {}

                    @Override
                    public void close(net.kyori.adventure.audience.Audience who) {}
                },
                world.messages,
                hand);
    }

    private CaptchaCommand captchaCommand(
            com.uxplima.auth.paper.adapter.inbound.screen.CaptchaScreen.Hand hand,
            com.uxplima.auth.paper.adapter.inbound.hold.ScreenOffer next) {
        return new CaptchaCommand(captchaScreen(hand), world.sessions::state, next, world.messages);
    }

    private void drain() {
        while (steve.nextComponentMessage() != null) {
            // Throw away what came before the part under test.
        }
    }

    /** The node the server would register for this handler, named by the shipped commands.conf. */
    private com.mojang.brigadier.tree.LiteralCommandNode<?> node(Object handler) {
        Path file = folder.resolve("commands.conf");
        try (java.io.InputStream shipped =
                java.util.Objects.requireNonNull(getClass().getResourceAsStream("/commands.conf"))) {
            java.nio.file.Files.copy(shipped, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .replacer(FromConfig.class, ConfiguredCommands.load(file).replacer());
        return AnnotatedCommands.buildNode(handler, resolvers);
    }

    private LoginCommand login() {
        return new LoginCommand(
                world.logIn,
                world.door,
                world::openingOf,
                world.moments,
                world.messages,
                world.replies,
                (reader, id, reason) -> offered.add(((org.bukkit.entity.Player) reader).getName() + ":"
                        + plain(java.util.Objects.requireNonNull(reason))),
                new com.uxplima.auth.paper.adapter.inbound.screen.FactorOffer() {
                    @Override
                    public void offer(
                            net.kyori.adventure.audience.Audience reader,
                            net.kyori.adventure.text.@org.jspecify.annotations.Nullable Component reason) {
                        offered.add(((org.bukkit.entity.Player) reader).getName() + ":code");
                    }

                    @Override
                    public java.util.concurrent.CompletableFuture<Void> answer(
                            com.uxplima.auth.paper.adapter.inbound.screen.Guest guest, String code) {
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }
                });
    }

    private RegisterCommand register(AuthSettings.Passwords passwords) {
        return new RegisterCommand(
                world.register,
                world.door,
                world::openingOf,
                () -> passwords,
                world.messages,
                world.replies,
                (reader, id, reason) -> offered.add(((org.bukkit.entity.Player) reader).getName() + ":"
                        + plain(java.util.Objects.requireNonNull(reason))));
    }

    private ChangePasswordCommand changePassword(AuthSettings.Passwords passwords) {
        return new ChangePasswordCommand(
                world.change, world.door, world.moments, () -> passwords, world.messages, world.replies);
    }

    private LogoutCommand logout() {
        return new LogoutCommand(world.logOut, world.door, world.moments, world.messages);
    }
}
