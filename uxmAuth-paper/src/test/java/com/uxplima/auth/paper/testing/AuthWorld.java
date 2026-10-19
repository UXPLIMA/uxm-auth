package com.uxplima.auth.paper.testing;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.adapter.outbound.AuthSchema;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.LocalEvents;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlAddressTallies;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.SqlRememberedSessions;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.application.protection.AccountCap;
import com.uxplima.auth.application.protection.AddressBans;
import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.premium.NameOwnership;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.WhenUnknown;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.Greeter;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.inbound.hold.Notices;
import com.uxplima.auth.paper.adapter.inbound.hold.Remembering;
import com.uxplima.auth.paper.adapter.inbound.hold.Reports;
import com.uxplima.auth.paper.adapter.inbound.hold.ScreenOffer;
import com.uxplima.auth.paper.adapter.inbound.hold.WaitingClock;
import com.uxplima.auth.paper.adapter.inbound.hold.Welcomes;
import com.uxplima.auth.paper.adapter.outbound.hold.HeldStateStore;
import com.uxplima.auth.paper.adapter.outbound.hold.HoldKeeper;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The whole way in, built the way the plugin builds it, over a real SQLite file and cheap hashes.
 *
 * <p>Every moment that fires is recorded, so a test reads which list ran rather than what the list drew.
 */
public final class AuthWorld implements AutoCloseable {

    public static final int MAX_ATTEMPTS = 3;

    public final Sessions sessions = new Sessions();
    public final MainThreadScheduler scheduler = new MainThreadScheduler();
    public final HashingLane lane = new HashingLane(1, 1, 16);
    public final Hashing hashing = new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4)));
    /** Today's password rules in this world; a test makes them stricter. */
    public PasswordPolicy policy = new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 0);

    public final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_760_000_000_000L), ZoneOffset.UTC);
    public final Database database;
    public final SqlAccountRepository accounts;
    public final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            LocaleSource.ofDefault(Locale.ENGLISH));
    public final List<AuthSettings.Moment> fired = new ArrayList<>();
    /** The state each screen offer was made for, in order. */
    public final List<AuthState> offered = new ArrayList<>();
    /** What the door told the proxy, in order. */
    public final List<String> reported = new ArrayList<>();

    public final Welcomes welcomes = new Welcomes();
    public final Notices notices;
    private final Map<UUID, Long> openings = new java.util.concurrent.ConcurrentHashMap<>();

    public final MomentRunner moments = (Player player, AuthSettings.Moment moment) -> fired.add(moment);
    public final AuthSettings.Passwords passwords;
    public final HoldKeeper keeper;
    public final Door door;
    public final Replies replies = new Replies(scheduler, messages, Logger.getLogger("uxmauth-test"));
    public final RegisterAccount register;
    public final LogIn logIn;
    public final ChangePassword change;
    public final LogOut logOut;
    public final SqlRememberedSessions remembered;
    public final Remember remember;
    public final Remembering remembering;
    public final AddressBans bans;

    /** How wrong passwords ban an address in this world; past the attempts of a connection until a test sets it. */
    public BanSteps banSteps = new BanSteps(
            List.of(Duration.ofMinutes(5), Duration.ofMinutes(30), Duration.ofHours(1), Duration.ofDays(1)),
            10,
            Duration.ofHours(24));

    /** How many password accounts one address may make in this world; a test lowers it. */
    public AccountCap.Settings accountSettings =
            new AccountCap.Settings(100, Duration.ofHours(24), new AddressRanges(List.of()));

    /** The shared-address hosts of this world; a test sets them. */
    public List<String> sharedHosts = List.of();

    /** The addresses the shared-address filters of this world connect from; a test sets them. */
    public List<String> sharedSources = List.of();

    /** When this world asks a captcha; off until a test sets it. */
    public com.uxplima.auth.application.protection.Captchas.Settings captchaSettings =
            new com.uxplima.auth.application.protection.Captchas.Settings(
                    com.uxplima.auth.application.protection.Captchas.When.OFF,
                    3,
                    5,
                    "ABCDEFGHJKMNPQRSTUVWXYZ23456789",
                    Duration.ofMinutes(60),
                    com.uxplima.auth.application.protection.Captchas.JavaScreen.DIALOG);

    public final com.uxplima.auth.application.protection.Captchas captchas;

    /** The backup codes of this world: eight of ten characters. */
    public static final com.uxplima.auth.domain.factor.BackupCodes BACKUP =
            new com.uxplima.auth.domain.factor.BackupCodes(8, 10);

    /** The codes of the second step in this world: six digits every 30 seconds, one step either side. */
    public static final com.uxplima.auth.domain.factor.Totp TOTP =
            new com.uxplima.auth.domain.factor.Totp(6, Duration.ofSeconds(30), 1);

    /** The app as this world sets it up. */
    public static final com.uxplima.auth.application.factor.SetUpTotp.Settings SETUP =
            new com.uxplima.auth.application.factor.SetUpTotp.Settings(
                    "Minecraft", "{player}", TOTP, Duration.ofMinutes(10), BACKUP);

    public final com.uxplima.auth.adapter.outbound.SqlFactors factors;
    /** What is known of each account's second factor while its player is here, for the placeholders. */
    public final com.uxplima.auth.application.factor.FactorStates factorStates;

    public final com.uxplima.auth.application.factor.SecondStep secondStep;
    public final com.uxplima.auth.application.factor.FactorCheck factorCheck;
    public final com.uxplima.auth.application.factor.ConfirmFactor confirmFactor;

    /** Which ways in ask the code in this world; a test changes it. */
    public com.uxplima.auth.application.factor.SecondStep.Settings stepSettings =
            new com.uxplima.auth.application.factor.SecondStep.Settings(true, false, MAX_ATTEMPTS);

    /** For whom sessions are on in this world; a test sets it. */
    public Remember.Settings sessionSettings = new Remember.Settings(Remember.Mode.CHOSEN, Duration.ofMinutes(60));

    public AuthWorld(Plugin plugin, Path folder, AuthSettings.Passwords passwords) {
        this.passwords = passwords;
        this.database = AuthSchema.migrate(Database.builder()
                .sqlite(folder.resolve("auth.db"))
                .poolName("uxmauth-test-" + UUID.randomUUID())
                .build());
        this.accounts = new SqlAccountRepository(database);
        this.keeper = new HoldKeeper(
                plugin,
                scheduler,
                new HeldStateStore(folder.resolve("held")),
                () -> hold(true),
                player -> sessions.state(player.getUniqueId())
                        .filter(state -> !(state instanceof AuthState.Authenticated))
                        .isPresent());
        WaitingClock waiting = new WaitingClock(
                scheduler,
                sessions,
                () -> new AuthSettings.Timeouts(Duration.ofSeconds(90), Duration.ofSeconds(60), Duration.ofSeconds(5)),
                clock,
                moments,
                messages);
        this.notices = new Notices(messages, moments);
        this.remembered = new SqlRememberedSessions(database, clock);
        this.remember = new Remember(remembered, () -> sessionSettings, this::sharedAddresses, clock::instant);
        this.remembering = new Remembering(remember, sessions, scheduler);
        this.history = new com.uxplima.auth.adapter.outbound.SqlLoginHistory(database);
        this.factors = new com.uxplima.auth.adapter.outbound.SqlFactors(
                database, com.uxplima.auth.adapter.outbound.SecretSeal.fromPassphrase("world"), clock);
        this.emails = new com.uxplima.auth.adapter.outbound.SqlEmails(database, clock);
        this.factorStates = new com.uxplima.auth.application.factor.FactorStates(factors, emails);
        this.door = new Door(
                sessions,
                keeper,
                waiting,
                moments,
                new Greeter(
                        moments,
                        (player, task) -> task.run(),
                        player -> sessions.state(player.getUniqueId())
                                .filter(state -> !(state instanceof AuthState.Authenticated))
                                .isPresent()),
                (ScreenOffer) (player, state) -> offered.add(state),
                welcomes,
                notices,
                new Reports() {
                    @Override
                    public void loggedIn(Player player, AuthMethod method) {
                        reported.add("in:" + method);
                    }

                    @Override
                    public void waiting(Player player) {
                        reported.add("waiting");
                    }
                },
                remembering,
                new com.uxplima.auth.paper.adapter.inbound.hold.LoginRecords(
                        history,
                        scheduler::async,
                        clock,
                        message -> {},
                        (account, name, address, method) -> {},
                        factorStates),
                Door.Duty.inTurn(lazy(this::setupDuty), lazy(this::addressDuty)));
        AccountCap cap = new AccountCap(accounts, () -> accountSettings, this::sharedAddresses, clock::instant);
        this.captchas = new com.uxplima.auth.application.protection.Captchas(
                () -> captchaSettings, new java.util.SplittableRandom(11), clock::instant);
        this.register = new RegisterAccount(
                accounts,
                hashing,
                lane,
                sessions,
                () -> policy,
                clock,
                cap::full,
                com.uxplima.auth.application.port.BreachedPasswords.NONE,
                captchas::blocksRegistration);
        this.bans =
                new AddressBans(new SqlAddressTallies(database), () -> banSteps, this::sharedAddresses, clock::instant);
        com.uxplima.auth.application.mail.MailLogin mailLogin =
                new com.uxplima.auth.application.mail.MailLogin(emails, mailCodes());
        this.secondStep =
                new com.uxplima.auth.application.factor.SecondStep(factors, emails, () -> stepSettings, account -> {
                    java.util.concurrent.CompletableFuture<?> unused =
                            mailLogin.send(account, java.util.Locale.ENGLISH);
                });
        this.owedSetups = new com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups(sessions);
        this.addressOwed = new com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups(sessions);
        com.uxplima.auth.application.mail.MailCodes codes = mailCodes();
        this.factorCheck = new com.uxplima.auth.application.factor.FactorCheck(
                factors,
                hashing,
                () -> TOTP,
                () -> BACKUP,
                new com.uxplima.auth.application.mail.MailedLoginCode(emails, codes));
        this.confirmFactor = new com.uxplima.auth.application.factor.ConfirmFactor(
                accounts, sessions, factorCheck, bans::failed, clock::instant);
        this.logIn =
                new LogIn(accounts, hashing, lane, sessions, clock, bans::failed, () -> policy, captchas, secondStep);
        this.change =
                new ChangePassword(accounts, hashing, lane, sessions, () -> policy, new LocalEvents(), remembered);
        this.logOut = new LogOut(sessions, () -> MAX_ATTEMPTS, remembered);
    }

    /** The shared-address filters of this world, as a test set them. */
    public SharedAddresses sharedAddresses() {
        return new SharedAddresses(new HostPatterns(sharedHosts), new AddressRanges(sharedSources));
    }

    /** The defaults the shipped file has. */
    public static AuthSettings.Passwords shippedPasswords() {
        return new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), true, true, MAX_ATTEMPTS);
    }

    /** A door at the hello under the automatic rule, with no web source to ask. */
    public AtTheDoor atTheDoor() {
        return new AtTheDoor(
                accounts,
                AuthWorld::shippedNames,
                new PremiumLookup(List::of, () -> Duration.ofMinutes(30), clock),
                () -> new NameOwnership(
                        NameRule.AUTOMATIC,
                        WhenUnknown.CRACKED,
                        new HostPatterns(List.of()),
                        new HostPatterns(List.of())),
                new NameMemory(() -> Duration.ofMinutes(5), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new NameMemory(() -> Duration.ofMinutes(10), clock),
                new SqlDisputes(database));
    }

    /** The name rules the shipped file has. */
    public static NamePolicy shippedNames() {
        return new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of());
    }

    /** A player with an account and this password, waiting to log in. */
    public void registered(Player player, String password) {
        Account account = new Account(
                player.getUniqueId(),
                new PlayerName(player.getName()),
                AccountKind.CRACKED,
                null,
                hashing.hash(Password.of(password)),
                clock.instant(),
                "127.0.0.1",
                null,
                null);
        accounts.insert(account);
        sessions.open(player.getUniqueId(), new AuthState.Registered(account, Attempts.fresh(MAX_ATTEMPTS)));
    }

    /** A player with no account yet. */
    public void unregistered(Player player) {
        sessions.open(player.getUniqueId(), new AuthState.Unregistered(new PlayerName(player.getName())));
    }

    /** Binds the player to the opening that holds their session now, as a join does. */
    public void bindOpening(Player player) {
        openings.put(
                player.getUniqueId(),
                sessions.held(player.getUniqueId()).orElseThrow().generation());
    }

    /** The opening a player is bound to, empty until {@link #bindOpening} ran for them. */
    public java.util.OptionalLong openingOf(Player player) {
        Long opening = openings.get(player.getUniqueId());
        return opening == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(opening);
    }

    /** Turns the app on for the player's account, and answers its secret. */
    public com.uxplima.auth.domain.factor.TotpSecret appOn(Player player) {
        return appOn(player.getName());
    }

    /** Turns the app on for the account under this name, and answers its secret. */
    public com.uxplima.auth.domain.factor.TotpSecret appOn(String name) {
        com.uxplima.auth.domain.factor.TotpSecret secret = new com.uxplima.auth.domain.factor.TotpSecret(new byte[20]);
        factors.setApp(accounts.findByName(new PlayerName(name)).orElseThrow().id(), secret);
        return secret;
    }

    /** The app's code now for this secret. */
    public String codeNow(com.uxplima.auth.domain.factor.TotpSecret secret) {
        return TOTP.code(secret.bytes(), TOTP.step(clock.instant()));
    }

    /** The mail table of this world. */
    public final com.uxplima.auth.adapter.outbound.SqlEmails emails;

    /** The values of every mail this world sent, in order. */
    public final List<java.util.Map<String, String>> mailed = java.util.Collections.synchronizedList(new ArrayList<>());

    /** What the mail server of this world answers. */
    public volatile com.uxplima.auth.application.port.Mailer.Outcome mailOutcome =
            com.uxplima.auth.application.port.Mailer.Outcome.SENT;

    /** The domains this world refuses an address at. */
    public volatile List<String> deniedDomains = List.of();

    /** The time mailed codes are sent and checked at, which a test moves on. */
    public volatile Instant mailNow = clock.instant();

    /** Codes by mail in this world: fifteen minutes, five tries, a minute apart, six digits. */
    public com.uxplima.auth.application.mail.MailCodes mailCodes() {
        return new com.uxplima.auth.application.mail.MailCodes(
                emails,
                (kind, to, language, values) -> {
                    mailed.add(values);
                    return java.util.concurrent.CompletableFuture.completedFuture(mailOutcome);
                },
                hashing,
                lane,
                () -> new com.uxplima.auth.application.mail.MailCodes.Settings(
                        Duration.ofMinutes(15), 5, Duration.ofSeconds(60), 6),
                bans::failed,
                new java.util.SplittableRandom(11),
                () -> mailNow);
    }

    /** {@code /2fa email on|off} in this world. */
    public com.uxplima.auth.paper.adapter.inbound.factor.MailToggle mailToggle() {
        return new com.uxplima.auth.paper.adapter.inbound.factor.MailToggle(
                new com.uxplima.auth.application.mail.MailFactor(
                        emails,
                        com.uxplima.auth.application.mail.MailFactor.by(factorCheck, clock::instant),
                        bans::failed),
                sessions,
                lane,
                replies,
                moments,
                messages);
    }

    /** {@code /recover} in this world, with this form for a Bedrock player. */
    public com.uxplima.auth.paper.adapter.inbound.mail.Recovery recovery(
            com.uxplima.auth.paper.adapter.inbound.mail.Recovery.Form form,
            java.util.function.Predicate<java.util.UUID> bedrock) {
        return new com.uxplima.auth.paper.adapter.inbound.mail.Recovery(
                recoverPassword(), sessions, form, bedrock, replies, moments, messages);
    }

    /** A forgotten password in this world: a code by mail, then the code and a new password. */
    public com.uxplima.auth.application.mail.RecoverPassword recoverPassword() {
        return new com.uxplima.auth.application.mail.RecoverPassword(
                sessions,
                emails,
                mailCodes(),
                accounts,
                hashing,
                lane,
                () -> policy,
                com.uxplima.auth.application.port.BreachedPasswords.NONE,
                remembered,
                new LocalEvents());
    }

    /** {@code /email} in this world, with these forms for a Bedrock player. */
    public com.uxplima.auth.paper.adapter.inbound.mail.EmailLink emailLink(
            com.uxplima.auth.paper.adapter.inbound.mail.EmailLink.Forms forms,
            java.util.function.Predicate<java.util.UUID> bedrock) {
        return new com.uxplima.auth.paper.adapter.inbound.mail.EmailLink(
                new com.uxplima.auth.application.mail.LinkEmail(
                        emails,
                        mailCodes(),
                        () -> new com.uxplima.auth.domain.mail.DomainRule(List.of(), deniedDomains),
                        lane,
                        () -> emailRequired),
                sessions,
                forms,
                bedrock,
                lane,
                replies,
                moments,
                messages,
                addressOwed::release);
    }

    /** Whether this world requires a confirmed address of a password account; off until a test sets it. */
    public boolean emailRequired;

    /** Whether this world's mail is on; a world with it off holds nobody for an address. */
    public boolean mailEnabled = true;

    /** Every login in this world. */
    public final com.uxplima.auth.adapter.outbound.SqlLoginHistory history;

    /** What staff read of an account in this world: ten recent logins, twenty other accounts. */
    public com.uxplima.auth.application.staff.AccountInfo accountInfo() {
        return new com.uxplima.auth.application.staff.AccountInfo(
                accounts,
                history,
                factors,
                emails,
                () -> new com.uxplima.auth.application.staff.AccountInfo.Settings(10, 20));
    }

    /** The players held for an address in this world. */
    public final com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups addressOwed;

    private com.uxplima.auth.paper.adapter.inbound.mail.@org.jspecify.annotations.Nullable AddressDuty address;

    /** The address duty of this world, made at first use; it shows the chat usage of /email. */
    public com.uxplima.auth.paper.adapter.inbound.mail.AddressDuty addressDuty() {
        com.uxplima.auth.paper.adapter.inbound.mail.AddressDuty made = address;
        if (made == null) {
            made = new com.uxplima.auth.paper.adapter.inbound.mail.AddressDuty(
                    addressOwed,
                    () -> emailRequired && mailEnabled,
                    id -> emails.address(id).isPresent(),
                    player -> {
                        java.util.concurrent.CompletableFuture<Void> unused = emailLink(
                                        com.uxplima.auth.paper.adapter.inbound.mail.EmailLink.Forms.NONE, id -> false)
                                .usage(player);
                    },
                    lane,
                    replies,
                    messages);
            address = made;
        }
        return made;
    }

    /** A duty made when it is first asked, so the world can build it from parts made after the door. */
    private static Door.Duty lazy(java.util.function.Supplier<? extends Door.Duty> made) {
        return new Door.Duty() {
            @Override
            public boolean owes(Player player) {
                return made.get().owes(player);
            }

            @Override
            public void hold(Player player, AuthState.Authenticated in, Runnable proceed) {
                made.get().hold(player, in, proceed);
            }
        };
    }

    /** Who must set the app up before playing: holders of this node; nobody when it is empty. */
    public String requiredNode = "";

    /** The players held for the app in this world. */
    public final com.uxplima.auth.paper.adapter.inbound.factor.OwedSetups owedSetups;

    private com.uxplima.auth.paper.adapter.inbound.factor.@org.jspecify.annotations.Nullable SetupDuty duty;

    /** The duty of this world, made at first use from the world's own setup. */
    private com.uxplima.auth.paper.adapter.inbound.factor.SetupDuty setupDuty() {
        com.uxplima.auth.paper.adapter.inbound.factor.SetupDuty made = duty;
        if (made == null) {
            made = new com.uxplima.auth.paper.adapter.inbound.factor.SetupDuty(
                    owedSetups,
                    () -> requiredNode,
                    id -> setUpTotp().active(id) || emails.mailsLoginCode(id),
                    factorSetup(),
                    id -> false,
                    lane,
                    replies,
                    messages);
            duty = made;
        }
        return made;
    }

    /** The setup of the app in this world, with no map and no form. */
    public com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup factorSetup() {
        return new com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup(
                setUpTotp(),
                sessions,
                owedSetups,
                () -> SETUP,
                com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup.Hand.NONE,
                com.uxplima.auth.paper.adapter.inbound.factor.FactorSetup.Form.NONE,
                id -> false,
                lane,
                replies,
                moments,
                messages);
    }

    /** Turning the app on and off in this world. */
    public com.uxplima.auth.application.factor.SetUpTotp setUpTotp() {
        return new com.uxplima.auth.application.factor.SetUpTotp(
                factors, factorCheck, () -> SETUP, bans::failed, new java.util.SplittableRandom(1), clock::instant);
    }

    public boolean isAuthenticated(Player player) {
        return sessions.state(player.getUniqueId()).orElse(null) instanceof AuthState.Authenticated;
    }

    public static AuthSettings.Hold hold(boolean on) {
        return new AuthSettings.Hold(on, on, on, List.of(), on, on, on, on, on, on, on, on, on, on, on);
    }

    @Override
    public void close() {
        lane.close();
        database.close();
    }
}
