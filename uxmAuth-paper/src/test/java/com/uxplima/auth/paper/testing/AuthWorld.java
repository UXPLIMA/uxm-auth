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
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.application.ChangePassword;
import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LogOut;
import com.uxplima.auth.application.RegisterAccount;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.paper.adapter.inbound.command.Replies;
import com.uxplima.auth.paper.adapter.inbound.hold.Door;
import com.uxplima.auth.paper.adapter.inbound.hold.Greeter;
import com.uxplima.auth.paper.adapter.inbound.hold.MomentRunner;
import com.uxplima.auth.paper.adapter.inbound.hold.WaitingClock;
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
    public final PasswordPolicy policy = new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 0);
    public final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_760_000_000_000L), ZoneOffset.UTC);
    public final Database database;
    public final SqlAccountRepository accounts;
    public final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            LocaleSource.ofDefault(Locale.ENGLISH));
    public final List<AuthSettings.Moment> fired = new ArrayList<>();
    public final MomentRunner moments = (Player player, AuthSettings.Moment moment) -> fired.add(moment);
    public final AuthSettings.Passwords passwords;
    public final HoldKeeper keeper;
    public final Door door;
    public final Replies replies = new Replies(scheduler, messages, Logger.getLogger("uxmauth-test"));
    public final RegisterAccount register;
    public final LogIn logIn;
    public final ChangePassword change;
    public final LogOut logOut;

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
                                .isPresent()));
        this.register = new RegisterAccount(accounts, hashing, lane, sessions, () -> policy, clock);
        this.logIn = new LogIn(accounts, hashing, lane, sessions, clock);
        this.change = new ChangePassword(accounts, hashing, lane, sessions, () -> policy);
        this.logOut = new LogOut(sessions, () -> MAX_ATTEMPTS);
    }

    /** The defaults the shipped file has. */
    public static AuthSettings.Passwords shippedPasswords() {
        return new AuthSettings.Passwords(6, 64, "\\S+", Set.of(), true, true, MAX_ATTEMPTS);
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
