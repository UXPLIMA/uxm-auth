package com.uxplima.auth.velocity.adapter.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Claim;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.velocity.testing.ProxyWorld;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** In the proxy's waiting world a player types /login or /register, and the same use cases answer. */
final class LimboLoginTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final PlayerName NAME = new PlayerName("Steve");
    private static final Audience READER = Audience.empty();
    private static final Claim.Waiting NONE = new Claim.Waiting(false, false, false);

    private final ProxyWorld world;

    LimboLoginTest(@TempDir Path folder) {
        this.world = new ProxyWorld(folder);
    }

    @AfterEach
    void close() {
        world.close();
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    private static LimboLogin.Outcome said(LimboLogin login, String line) throws Exception {
        return login.say(READER, STEVE, NAME, line, "127.0.0.1").get(10, TimeUnit.SECONDS);
    }

    private static String told(LimboLogin.Outcome outcome) {
        assertThat(outcome).isInstanceOf(LimboLogin.Outcome.Stay.class);
        return plain(((LimboLogin.Outcome.Stay) outcome).said());
    }

    @Test
    @DisplayName("a new name is told to register, and two matching passwords make the account and let it in")
    void aNewNameRegisters() throws Exception {
        LimboLogin login = world.limboLogin(true);

        assertThat(plain(login.arrive(READER, STEVE, NAME, NONE).getLast())).contains("/register");
        assertThat(said(login, "/register hunter22 hunter22"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
        assertThat(world.accounts.findByName(NAME)).isPresent();
    }

    @Test
    @DisplayName("a register that wants the password twice says so, and two that differ are told apart")
    void aRegisterIsChecked() throws Exception {
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/register hunter22"))).isNotBlank();
        assertThat(told(said(login, "/reg hunter22 hunter23"))).contains("not the same");
        assertThat(told(said(login, "/register abc abc"))).contains("6");
    }

    @Test
    @DisplayName("where the operator asks the password once, one is enough to register")
    void onePasswordIsEnoughWhenAskedOnce() throws Exception {
        LimboLogin login = world.limboLogin(false);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(said(login, "/register hunter22")).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
    }

    @Test
    @DisplayName("a known name logs in with its password, in any of its words, and a wrong one says the tries left")
    void aKnownNameLogsIn() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);

        LimboLogin login = world.limboLogin(true);
        assertThat(plain(login.arrive(READER, STEVE, NAME, NONE).getLast())).contains("/login");
        assertThat(told(said(login, "/login wrongpass"))).contains("2");
        assertThat(said(login, "/giris hunter22")).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("too many wrong passwords send the player away with the reason")
    void tooManyWrongPasswordsSendAway() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        said(login, "/login one111");
        said(login, "/login two222");

        assertThat(said(login, "/login three3")).isInstanceOf(LimboLogin.Outcome.Out.class);
    }

    @Test
    @DisplayName("anything else said in the waiting world is answered with what to type")
    void anythingElseIsAnsweredWithWhatToType() throws Exception {
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "hello"))).contains("/register");
        assertThat(told(said(login, "/spawn"))).contains("/register");
        assertThat(told(said(login, "/login"))).contains("/register");
    }

    @Test
    @DisplayName(
            "a player the door let in unverified makes an unverified account, and one whose handshake failed reads why")
    void theDoorsFlagsReachTheWait() throws Exception {
        LimboLogin login = world.limboLogin(true);

        java.util.List<Component> explained = login.arrive(READER, STEVE, NAME, new Claim.Waiting(false, true, false));
        assertThat(explained).hasSize(2);
        assertThat(plain(explained.getFirst())).contains("paid");

        java.util.List<Component> lines = login.arrive(READER, STEVE, NAME, new Claim.Waiting(true, false, false));

        assertThat(lines).hasSize(1);
        assertThat(said(login, "/register hunter22 hunter22"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
        assertThat(world.accounts.findByName(NAME))
                .hasValueSatisfying(account -> assertThat(account.unverified()).isTrue());
    }

    @Test
    @DisplayName("a wrong password that bans the address sends the player away with the time left")
    void aBanSendsAway() throws Exception {
        java.util.List<String> hosts = new java.util.ArrayList<>();
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        LimboLogin login = world.limboLogin(true, (address, host) -> {
            hosts.add(address + "@" + host);
            return java.util.Optional.of(java.time.Duration.ofMinutes(5));
        });
        login.arrive(READER, STEVE, NAME, NONE, "mc.tcpshield.com");

        LimboLogin.Outcome outcome = said(login, "/login one111");

        assertThat(outcome)
                .isInstanceOfSatisfying(
                        LimboLogin.Outcome.Out.class,
                        out -> assertThat(plain(out.reason())).contains("5m"));
        assertThat(hosts).containsExactly("127.0.0.1@mc.tcpshield.com");
    }

    @Test
    @DisplayName("a right password the rules outgrew holds the player until /changepassword sets a new one")
    void aWeakPasswordIsReplacedInTheWaitingWorld() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        world.policy = new PasswordPolicy(10, 64, Pattern.compile("\\S+"), Set.of(), 0);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/login hunter22"))).contains("10").contains("/changepassword");
        assertThat(plain(login.reminder(READER, STEVE))).contains("/changepassword");
        assertThat(told(said(login, "/changepassword hunter22"))).contains("old password, then the new");
        assertThat(told(said(login, "/changepassword wrongpass longenough1"))).contains("wrong");
        assertThat(told(said(login, "/changepassword hunter22 short1"))).contains("10");
        assertThat(said(login, "/changepassword hunter22 longenough1"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
        assertThat(world.accounts.findByName(NAME))
                .hasValueSatisfying(account -> assertThat(world.hashing.verify(
                                Password.of("longenough1"), java.util.Objects.requireNonNull(account.password())))
                        .isInstanceOf(Verification.Right.class));
    }

    @Test
    @DisplayName("/changepassword before a login is answered with what to type")
    void aChangeBeforeTheLoginIsNotTaken() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/changepassword hunter22 longenough1"))).contains("/login");
        assertThat(told(said(login, "/changepassword hunter22"))).contains("/login");
    }

    private static String captcha(LimboLogin.Outcome outcome) {
        assertThat(outcome).isInstanceOf(LimboLogin.Outcome.Captcha.class);
        return plain(((LimboLogin.Outcome.Captcha) outcome).said());
    }

    @Test
    @DisplayName("a registration the captcha stands before draws the code on the map, and /captcha answers it")
    void aCaptchaStandsBeforeTheRegistration() throws Exception {
        world.captchaSettings = settings(com.uxplima.auth.application.protection.Captchas.When.REGISTER);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(login.standing(READER, STEVE))
                .hasValueSatisfying(outcome -> assertThat(captcha(outcome)).contains("/captcha"));
        assertThat(captcha(said(login, "/register hunter22 hunter22"))).contains("Solve the captcha first");
        String first = world.captchas.code(STEVE).orElseThrow();
        assertThat(captcha(said(login, "/captcha WRONG"))).contains("not the code");
        String second = world.captchas.code(STEVE).orElseThrow();
        assertThat(second).isNotEqualTo(first);

        assertThat(told(said(login, "/dogrula " + second.toLowerCase(java.util.Locale.ROOT))))
                .contains("solved")
                .contains("/register");
        assertThat(login.standing(READER, STEVE)).isEmpty();
        assertThat(said(login, "/register hunter22 hunter22"))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.REGISTERED));
    }

    @Test
    @DisplayName(
            "a login after failures waits for the captcha, /captcha with nothing shown says so, and a leave forgets")
    void aCaptchaStandsBeforeTheLoginAfterFailures() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        world.captchaSettings = settings(com.uxplima.auth.application.protection.Captchas.When.FAILURES);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/captcha ABCDE"))).contains("no captcha");
        assertThat(login.standing(READER, STEVE)).isEmpty();
        assertThat(told(said(login, "/login wrongpass"))).contains("2");
        assertThat(login.standing(READER, STEVE)).isPresent();
        assertThat(captcha(said(login, "/login hunter22"))).contains("Solve the captcha first");
        assertThat(world.captchas.code(STEVE)).isPresent();

        login.leave(STEVE);

        assertThat(world.captchas.code(STEVE)).isEmpty();
    }

    private static com.uxplima.auth.application.protection.Captchas.Settings settings(
            com.uxplima.auth.application.protection.Captchas.When when) {
        return new com.uxplima.auth.application.protection.Captchas.Settings(
                when,
                1,
                5,
                "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                java.time.Duration.ofMinutes(60),
                com.uxplima.auth.application.protection.Captchas.JavaScreen.MAP);
    }

    @Test
    @DisplayName("an address past the accounts limit stays and reads why")
    void aFullAddressStays() throws Exception {
        LimboLogin login =
                world.limboLogin(true, (address, host) -> java.util.Optional.empty(), (address, host) -> true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/register hunter22 hunter22"))).contains("Too many accounts");
        assertThat(world.accounts.findByName(NAME)).isEmpty();
    }

    private com.uxplima.auth.domain.factor.TotpSecret registeredWithTheApp() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        return world.appOn(NAME);
    }

    @Test
    @DisplayName("a right password on an account with the app asks the code, and /2fa with the app's code lets in")
    void thePasswordThenTheCode() throws Exception {
        com.uxplima.auth.domain.factor.TotpSecret secret = registeredWithTheApp();
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/login hunter22")))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_CODE_NEEDED)));
        assertThat(login.registered(STEVE))
                .as("the time to log in, not to register, runs")
                .isTrue();
        assertThat(plain(login.reminder(READER, STEVE)))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_CODE_NEEDED)));
        assertThat(told(said(login, "/2fa 000000")))
                .isEqualTo(plain(world.messages.render(
                        READER, Keys.FACTOR_LOGIN_WRONG, com.uxplima.uxmlib.text.Text.placeholder("left", "2"))));
        assertThat(said(login, "/2fa " + world.codeNow(secret)))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("wrong codes run out like wrong passwords, and the last sends the player away")
    void wrongCodesRunOut() throws Exception {
        registeredWithTheApp();
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);
        said(login, "/login hunter22");

        said(login, "/2fa 000000");
        said(login, "/2fa 000000");

        assertThat(said(login, "/2fa 000000")).isInstanceOf(LimboLogin.Outcome.Out.class);
    }

    @Test
    @DisplayName("a player the door let past the password owes only the code, and it lets them in by the door's way")
    void aPlayerOwingTheCodeTypesOnlyTheCode() throws Exception {
        com.uxplima.auth.domain.factor.TotpSecret secret = registeredWithTheApp();
        LimboLogin login = world.limboLogin(true);

        assertThat(plain(login.arrive(READER, STEVE, NAME, NONE, "", java.util.Optional.of(AuthMethod.SESSION))
                        .getLast()))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_CODE_NEEDED)));
        assertThat(told(said(login, "/login hunter22")))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_CODE_NEEDED)));
        assertThat(said(login, "/2fa " + world.codeNow(secret)))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.SESSION));
    }

    @Test
    @DisplayName(
            "an account that takes its code by mail gets it as the password passes, and the mailed code lets it in")
    void aMailedCodeLetsThePlayerIn() throws Exception {
        registeredWithTheApp();
        java.util.UUID account = world.accounts.findByName(NAME).orElseThrow().id();
        world.factors.removeApp(account);
        world.emails.setAddress(
                account,
                com.uxplima.auth.domain.mail.EmailAddress.parse("s@example.net").orElseThrow());
        world.emails.setMailsLoginCode(account, true);
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/login hunter22")))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_CODE_NEEDED)));
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (world.mailed.isEmpty() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        String code = java.util.Objects.requireNonNull(world.mailed.getLast().get("code"), "code");

        assertThat(said(login, "/2fa " + code)).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("a backup code, typed as it was shown, lets the player in once")
    void aBackupCodeLetsThePlayerIn() throws Exception {
        registeredWithTheApp();
        java.util.List<String> codes = world.factorCheck.newBackupCodes(
                world.accounts.findByName(NAME).orElseThrow().id(), new java.util.SplittableRandom(2));
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);
        said(login, "/login hunter22");

        assertThat(said(login, "/2fa " + com.uxplima.auth.domain.factor.BackupCodes.shown(codes.get(5))))
                .isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
        assertThat(world.factors.backupCodes(
                        world.accounts.findByName(NAME).orElseThrow().id()))
                .hasSize(7);
    }

    @Test
    @DisplayName("/2fa from a player no code is asked of says so")
    void noCodeIsAsked() throws Exception {
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);

        assertThat(told(said(login, "/2fa 123456")))
                .isEqualTo(plain(world.messages.render(READER, Keys.FACTOR_NOTHING_ASKED)));
    }

    /** Steve has an account with a confirmed address, and waits at the login. */
    private LimboLogin waitingWithAnAddress() throws Exception {
        LimboLogin first = world.limboLogin(true);
        first.arrive(READER, STEVE, NAME, NONE);
        said(first, "/register hunter22 hunter22");
        first.leave(STEVE);
        world.emails.setAddress(
                world.accounts.findByName(NAME).orElseThrow().id(),
                com.uxplima.auth.domain.mail.EmailAddress.parse("steve@example.net")
                        .orElseThrow());
        LimboLogin login = world.limboLogin(true);
        login.arrive(READER, STEVE, NAME, NONE);
        return login;
    }

    private String recoveryCode() {
        return java.util.Objects.requireNonNull(world.mailed.getLast().get("code"), "code");
    }

    @Test
    @DisplayName("/recover in the waiting world mails a code; the code and a new password set it, and it logs in")
    void aForgottenPasswordIsRecovered() throws Exception {
        LimboLogin login = waitingWithAnAddress();

        assertThat(told(said(login, "/kurtar"))).contains("s***@e***.net");
        assertThat(told(said(login, "/recover " + recoveryCode() + " fresh7777")))
                .isEqualTo(plain(world.messages.render(READER, Keys.RECOVER_DONE)));
        assertThat(told(said(login, "/login hunter22"))).contains("Tries left");
        assertThat(said(login, "/login fresh7777")).isEqualTo(new LimboLogin.Outcome.In(AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("through the proxy, a second request inside the cooldown says when, a wrong code its tries left,"
            + " and an expired code is refused")
    void theCodeIsGuarded() throws Exception {
        LimboLogin login = waitingWithAnAddress();
        said(login, "/recover");
        world.clock.pass(java.time.Duration.ofSeconds(45));

        assertThat(told(said(login, "/recover"))).contains("15");
        assertThat(world.mailed).hasSize(1);
        assertThat(told(said(login, "/recover 000000 fresh7777"))).contains("4");
        world.clock.pass(java.time.Duration.ofMinutes(16));
        assertThat(told(said(login, "/recover " + recoveryCode() + " fresh7777")))
                .isEqualTo(plain(world.messages.render(READER, Keys.RECOVER_EXPIRED)));
    }

    @Test
    @DisplayName("a wrong recovery code counts toward the address ban, and the ban sends the player away")
    void wrongCodesBan() throws Exception {
        LimboLogin login = waitingWithAnAddress();
        said(login, "/recover");
        java.util.List<String> counted = new java.util.concurrent.CopyOnWriteArrayList<>();
        world.mailFailures = (address, host) -> {
            counted.add(address);
            return java.util.Optional.of(java.time.Duration.ofMinutes(30));
        };

        assertThat(said(login, "/recover 000000 fresh7777")).isInstanceOf(LimboLogin.Outcome.Out.class);
        assertThat(counted).containsExactly("127.0.0.1");
    }

    @Test
    @DisplayName("/recover with one word, or three, says how to type it and spends nothing")
    void halfTypedSaysHow() throws Exception {
        LimboLogin login = waitingWithAnAddress();
        said(login, "/recover");
        String usage = plain(world.messages.render(READER, Keys.RECOVER_USAGE));

        assertThat(told(said(login, "/recover " + recoveryCode()))).isEqualTo(usage);
        assertThat(told(said(login, "/recover " + recoveryCode() + " fresh7777 again")))
                .isEqualTo(usage);
        assertThat(told(said(login, "/recover " + recoveryCode() + " fresh7777")))
                .isEqualTo(plain(world.messages.render(READER, Keys.RECOVER_DONE)));
    }
}
