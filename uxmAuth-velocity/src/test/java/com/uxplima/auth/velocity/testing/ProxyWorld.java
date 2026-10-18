package com.uxplima.auth.velocity.testing;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.SqlRememberedSessions;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.velocity.adapter.inbound.LimboLogin;
import com.uxplima.auth.velocity.adapter.inbound.ProxyDoor;
import com.uxplima.auth.velocity.adapter.outbound.ProxyLocales;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * A proxy's door on a real SQLite file, with the web answer, the rule, the conflict strategy and the scheme a
 * test sets before it builds the door.
 */
public final class ProxyWorld implements AutoCloseable {

    public static final UUID MOJANG = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    public static final String PLAY = "play.example.org";
    public static final String PREMIUM_HOST = "premium.example.org";

    /** A clock a test moves on by hand. */
    public static final class MovingClock extends Clock {
        private Instant now = Instant.parse("2026-10-17T20:00:00Z");

        public void pass(Duration time) {
            now = now.plus(time);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    public final MovingClock clock = new MovingClock();
    public final Database database;
    public final SqlAccountRepository accounts;
    public final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new ProxyLocales(() -> LanguageSettings.following(Locale.ENGLISH)));
    public final DoorWords words = new DoorWords(messages);
    public final HashingLane lane = new HashingLane(1, 1, 16);
    public final Hashing hashing = new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4)));
    /** When this world asks a captcha; off until a test sets it. */
    public com.uxplima.auth.application.protection.Captchas.Settings captchaSettings =
            new com.uxplima.auth.application.protection.Captchas.Settings(
                    com.uxplima.auth.application.protection.Captchas.When.OFF,
                    1,
                    5,
                    "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                    Duration.ofMinutes(60),
                    com.uxplima.auth.application.protection.Captchas.JavaScreen.MAP);

    public final com.uxplima.auth.application.protection.Captchas captchas =
            new com.uxplima.auth.application.protection.Captchas(
                    () -> captchaSettings, new java.util.SplittableRandom(13), Instant::now);

    /** Today's password rules in this world; a test makes them stricter. */
    public PasswordPolicy policy = new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 0);

    public final Sessions sessions = new Sessions();
    public PremiumAnswer web = new PremiumAnswer.Premium(MOJANG, "Notch");
    public NameRule rule = NameRule.AUTOMATIC;
    public ConflictStrategy conflict = ConflictStrategy.RENAME;
    public UuidScheme scheme = UuidScheme.KEEP_FIRST;

    /** The codes of the second step on this proxy: six digits every 30 seconds, one step either side. */
    public static final com.uxplima.auth.domain.factor.Totp TOTP =
            new com.uxplima.auth.domain.factor.Totp(6, Duration.ofSeconds(30), 1);

    public final com.uxplima.auth.adapter.outbound.SqlFactors factors;
    public final com.uxplima.auth.application.factor.SecondStep secondStep;

    /** Which ways in ask the code on this proxy; a test changes it. */
    public com.uxplima.auth.application.factor.SecondStep.Settings stepSettings =
            new com.uxplima.auth.application.factor.SecondStep.Settings(true, false, 3);

    public ProxyWorld(Path folder) {
        this.database = AuthDatabase.open(
                new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve("accounts.db"), "", "", 1), "proxy-test");
        this.accounts = new SqlAccountRepository(database);
        this.factors = new com.uxplima.auth.adapter.outbound.SqlFactors(
                database, com.uxplima.auth.adapter.outbound.SecretSeal.fromPassphrase("network"), clock);
        this.secondStep = new com.uxplima.auth.application.factor.SecondStep(factors, () -> stepSettings);
    }

    /** Turns the app on for the account under this name, and answers its secret. */
    public com.uxplima.auth.domain.factor.TotpSecret appOn(PlayerName name) {
        com.uxplima.auth.domain.factor.TotpSecret secret = new com.uxplima.auth.domain.factor.TotpSecret(new byte[20]);
        factors.setApp(accounts.findByName(name).orElseThrow().id(), secret);
        return secret;
    }

    /** The app's code now for this secret. */
    public String codeNow(com.uxplima.auth.domain.factor.TotpSecret secret) {
        return TOTP.code(secret.bytes(), TOTP.step(clock.instant()));
    }

    /** The door, built with what the test set. */
    public ProxyDoor door() {
        SqlDisputes disputes = new SqlDisputes(database);
        NamePolicy policy = new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of());
        AtTheDoor atTheDoor = new AtTheDoor(
                accounts,
                () -> policy,
                new PremiumLookup(() -> List.of(name -> web), () -> Duration.ZERO, clock),
                () -> new NameOwnership(
                        rule,
                        WhenUnknown.CRACKED,
                        new HostPatterns(List.of(PREMIUM_HOST)),
                        new HostPatterns(List.of())),
                new NameMemory(() -> Duration.ofMinutes(5), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                disputes);
        return new ProxyDoor(
                atTheDoor,
                new LetPremiumIn(accounts, disputes, () -> conflict, () -> "_", scheme, clock),
                new Moves(accounts),
                new ReadTheName(accounts, () -> policy, () -> 3),
                () -> new DomainMark("+", false),
                scheme,
                clock);
    }

    /** The password login of the proxy's waiting world, with /login, /giris and /register as its words. */
    public LimboLogin limboLogin(boolean asksTwice) {
        return limboLogin(asksTwice, (address, host) -> java.util.Optional.empty());
    }

    /** The same, with wrong passwords counted against their address here. */
    public LimboLogin limboLogin(boolean asksTwice, LogIn.Failures failures) {
        return limboLogin(asksTwice, failures, (address, host) -> false);
    }

    /** The same, with a limit on the accounts one address may make. */
    public LimboLogin limboLogin(boolean asksTwice, LogIn.Failures failures, RegisterAccount.Cap cap) {
        NamePolicy names = new NamePolicy(Pattern.compile("[A-Za-z0-9_+]+"), 3, 16, List.of());
        return new LimboLogin(
                new ReadTheName(accounts, () -> names, () -> 3),
                new LogIn(accounts, hashing, lane, sessions, clock, failures, () -> policy, captchas, secondStep),
                new RegisterAccount(
                        accounts,
                        hashing,
                        lane,
                        sessions,
                        () -> policy,
                        clock,
                        cap,
                        com.uxplima.auth.application.port.BreachedPasswords.NONE,
                        captchas::blocksRegistration),
                new ChangePassword(
                        accounts,
                        hashing,
                        lane,
                        sessions,
                        () -> policy,
                        new LocalEvents(),
                        new SqlRememberedSessions(database, clock)),
                captchas,
                sessions,
                messages,
                new LimboLogin.Words(
                        () -> Set.of("login", "l", "giris"),
                        () -> Set.of("register", "reg"),
                        () -> Set.of("changepassword", "cp"),
                        () -> Set.of("captcha", "dogrula"),
                        () -> Set.of("2fa", "dogrulama")),
                () -> asksTwice,
                secondStep,
                codes(failures));
    }

    /** The code of the second step on this proxy, checked at once. */
    private LimboLogin.CodeCheck codes(LogIn.Failures failures) {
        com.uxplima.auth.application.factor.ConfirmFactor confirm =
                new com.uxplima.auth.application.factor.ConfirmFactor(
                        accounts, factors, sessions, () -> TOTP, failures, clock::instant);
        return (id, code, address) ->
                java.util.concurrent.CompletableFuture.completedFuture(confirm.confirm(id, code, address));
    }

    @Override
    public void close() {
        lane.close();
        database.close();
    }
}
