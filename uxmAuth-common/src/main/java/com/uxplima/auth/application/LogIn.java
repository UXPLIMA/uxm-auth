package com.uxplima.auth.application;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Checks a typed password against the account a connection is waiting on.
 *
 * <p>A right password on an older hash is stored again in the current algorithm, because a login is the only
 * moment the clear text exists. A wrong one spends an attempt. Nothing is written for a player who left
 * while the check ran.
 */
public final class LogIn {

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Clock clock;

    public LogIn(AccountRepository accounts, Hashing hashing, HashingLane lane, Sessions sessions, Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The password is wiped once the answer is known, whatever it is. */
    public CompletableFuture<LoginResult> run(UUID connection, Password password, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, password, address), password);
    }

    private CompletableFuture<LoginResult> attempt(UUID connection, Password password, String address) {
        AuthState state = sessions.state(connection).orElse(null);
        return switch (state) {
            case null -> CompletableFuture.completedFuture(new LoginResult.Gone());
            case AuthState.Unregistered unregistered ->
                CompletableFuture.completedFuture(new LoginResult.NotRegistered());
            case AuthState.Authenticated authenticated ->
                CompletableFuture.completedFuture(new LoginResult.AlreadyLoggedIn());
            case AuthState.Registered waiting -> check(connection, waiting, password, address);
        };
    }

    private CompletableFuture<LoginResult> check(
            UUID connection, AuthState.Registered waiting, Password password, String address) {
        if (waiting.attempts().exhausted()) {
            return CompletableFuture.completedFuture(new LoginResult.TooManyAttempts());
        }
        PasswordHash stored = waiting.account().password();
        if (stored == null) {
            return CompletableFuture.completedFuture(new LoginResult.NotRegistered());
        }
        return lane.submit(() -> settle(connection, waiting.account(), hashing.verify(password, stored), address));
    }

    private LoginResult settle(UUID connection, Account account, Verification verification, String address) {
        if (!sessions.isOpen(connection)) {
            return new LoginResult.Gone();
        }
        return switch (verification) {
            case Verification.Wrong wrong -> spendAnAttempt(connection);
            case Verification.Right right -> logIn(connection, account.loggedIn(clock.instant(), address));
            case Verification.RightButRehash rehash ->
                logIn(connection, account.withPassword(rehash.fresh()).loggedIn(clock.instant(), address));
        };
    }

    private LoginResult spendAnAttempt(UUID connection) {
        AuthState after = sessions.update(
                        connection,
                        current -> current instanceof AuthState.Registered waiting
                                ? new AuthState.Registered(
                                        waiting.account(), waiting.attempts().failed())
                                : current)
                .orElse(null);
        return switch (after) {
            case null -> new LoginResult.Gone();
            case AuthState.Registered waiting ->
                waiting.attempts().exhausted()
                        ? new LoginResult.TooManyAttempts()
                        : new LoginResult.Wrong(waiting.attempts().left());
            case AuthState.Authenticated authenticated -> new LoginResult.AlreadyLoggedIn();
            case AuthState.Unregistered unregistered -> new LoginResult.NotRegistered();
        };
    }

    private LoginResult logIn(UUID connection, Account updated) {
        accounts.update(updated);
        return sessions.update(
                        connection,
                        current -> current instanceof AuthState.Registered
                                ? new AuthState.Authenticated(updated, AuthMethod.PASSWORD)
                                : current)
                .<LoginResult>map(after -> new LoginResult.LoggedIn(updated))
                .orElseGet(LoginResult.Gone::new);
    }
}
