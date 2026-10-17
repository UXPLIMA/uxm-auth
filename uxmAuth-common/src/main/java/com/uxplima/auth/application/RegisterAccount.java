package com.uxplima.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Makes an account for a connection that has none, and logs it in.
 *
 * <p>Everything cheap is decided here, on the caller's thread: the connection, the confirmation, the policy.
 * Only a password that will be kept costs a hash, and the hash runs on the lane.
 */
public final class RegisterAccount {

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Supplier<PasswordPolicy> policy;
    private final Clock clock;

    public RegisterAccount(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Both passwords are wiped once the answer is known, whatever it is. */
    public CompletableFuture<RegisterResult> run(
            UUID connection, PlayerName name, Password password, Password confirm, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(confirm, "confirm");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, name, password, confirm, address), password, confirm);
    }

    private CompletableFuture<RegisterResult> attempt(
            UUID connection, PlayerName name, Password password, Password confirm, String address) {
        AuthState state = sessions.state(connection).orElse(null);
        if (state == null) {
            return CompletableFuture.completedFuture(new RegisterResult.Gone());
        }
        if (!(state instanceof AuthState.Unregistered)) {
            return CompletableFuture.completedFuture(new RegisterResult.AlreadyRegistered());
        }
        if (!password.sameAs(confirm)) {
            return CompletableFuture.completedFuture(new RegisterResult.Mismatch());
        }
        PasswordVerdict verdict = policy.get().check(password);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return CompletableFuture.completedFuture(new RegisterResult.Rejected(verdict));
        }
        return lane.submit(() -> store(connection, name, hashing.hash(password), address));
    }

    private RegisterResult store(UUID connection, PlayerName name, PasswordHash hash, String address) {
        if (!sessions.isOpen(connection)) {
            return new RegisterResult.Gone();
        }
        Instant now = clock.instant();
        // The connection's UUID is the account's: the one the server gave this player. The schemes that
        // choose another come with the name rules, through a port of their own.
        Account account = new Account(connection, name, AccountKind.CRACKED, null, hash, now, address, now, address);
        return switch (accounts.insert(account)) {
            case AccountRepository.NameTaken taken -> new RegisterResult.NameTaken(taken.stored());
            case AccountRepository.Inserted inserted ->
                sessions.update(
                                connection,
                                current -> current instanceof AuthState.Unregistered
                                        ? new AuthState.Authenticated(account, AuthMethod.REGISTERED)
                                        : current)
                        .<RegisterResult>map(after -> new RegisterResult.Created(account))
                        .orElseGet(RegisterResult.Gone::new);
        };
    }
}
