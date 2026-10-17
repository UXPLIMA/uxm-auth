package com.uxplima.auth.testing;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;

/** The parts every use case test builds the same way: cheap hashes, a small lane, a fixed clock. */
public final class AuthFixture implements AutoCloseable {

    public static final Instant NOW = Instant.ofEpochMilli(1_760_000_000_000L);
    public static final int MAX_ATTEMPTS = 3;

    public final InMemoryAccounts accounts = new InMemoryAccounts();
    public final Sessions sessions = new Sessions();
    public final Hashing hashing = new Hashing(new Argon2idHasher(1024, 1, 1), List.of(new BcryptHasher(4)));
    public final PasswordPolicy policy = new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 72);
    public final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    public final HashingLane lane;

    public AuthFixture(int threads) {
        this.lane = new HashingLane(threads, threads, 16);
    }

    /** A connection that has an account with this password, waiting to log in. */
    public UUID registered(String name, String password) {
        Account account = account(name, hashing.hash(Password.of(password)));
        accounts.seed(account);
        UUID connection = UUID.randomUUID();
        sessions.open(connection, new AuthState.Registered(account, Attempts.fresh(MAX_ATTEMPTS)));
        return connection;
    }

    /** A connection with no account under its name. */
    public UUID unregistered(String name) {
        UUID connection = UUID.randomUUID();
        sessions.open(connection, new AuthState.Unregistered(new PlayerName(name)));
        return connection;
    }

    public static Account account(String name, PasswordHash password) {
        return new Account(
                UUID.randomUUID(),
                new PlayerName(name),
                AccountKind.CRACKED,
                null,
                password,
                NOW,
                "10.0.0.1",
                null,
                null);
    }

    @Override
    public void close() {
        lane.close();
    }
}
