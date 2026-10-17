package com.uxplima.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Checks a typed password against the account a connection is waiting on.
 *
 * <p>The attempt is spent before the hash runs, in one step on the session, so logins sent together cannot
 * try more passwords than the limit allows. The password is checked against the row as it is now, not as it
 * was read at the join, because another server or a member of staff may have changed it since. A right
 * password on an older hash is stored again in the current algorithm, and nothing is written for a
 * connection that closed, or was opened again, while the check ran.
 */
public final class LogIn {

    private static final long NO_OPENING = -1;

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
        return Wiping.after(() -> attempt(connection, NO_OPENING, password, address), password);
    }

    /**
     * The same, bound to one opening of the session: when another connection under this id opened it again
     * since, the answer is gone and nothing is checked or opened.
     */
    public CompletableFuture<LoginResult> run(UUID connection, long opening, Password password, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, opening, password, address), password);
    }

    private CompletableFuture<LoginResult> attempt(UUID connection, long opening, Password password, String address) {
        Sessions.Held held = sessions.held(connection)
                .filter(found -> opening == NO_OPENING || found.generation() == opening)
                .orElse(null);
        if (held == null) {
            return CompletableFuture.completedFuture(new LoginResult.Gone());
        }
        AtomicReference<AuthState.Registered> reserved = new AtomicReference<>();
        AuthState after = sessions.update(connection, held.generation(), current -> spend(current, reserved))
                .orElse(null);
        AuthState.Registered spent = reserved.get();
        if (spent == null) {
            return CompletableFuture.completedFuture(refusal(after));
        }
        long generation = held.generation();
        return lane.submit(() -> check(connection, generation, spent, password, address));
    }

    /** Spends one attempt on a connection that has one left, and remembers what it became. */
    private static AuthState spend(AuthState current, AtomicReference<AuthState.Registered> reserved) {
        if (current instanceof AuthState.Registered waiting
                && !waiting.attempts().exhausted()) {
            AuthState.Registered spent = new AuthState.Registered(
                    waiting.account(), waiting.attempts().failed());
            reserved.set(spent);
            return spent;
        }
        return current;
    }

    /** Why no attempt could be spent. */
    private static LoginResult refusal(@Nullable AuthState after) {
        return switch (after) {
            case null -> new LoginResult.Gone();
            case AuthState.Unregistered unregistered -> new LoginResult.NotRegistered();
            case AuthState.Authenticated authenticated -> new LoginResult.AlreadyLoggedIn();
            case AuthState.Registered waiting -> new LoginResult.TooManyAttempts();
        };
    }

    private LoginResult check(
            UUID connection, long generation, AuthState.Registered spent, Password password, String address) {
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        Account current = accounts.findById(spent.account().id()).orElse(null);
        PasswordHash stored = current == null ? null : current.password();
        if (current == null || stored == null) {
            return new LoginResult.NotRegistered();
        }
        return switch (hashing.verify(password, stored)) {
            case Verification.Wrong wrong -> wrongAnswer(spent.attempts());
            case Verification.Right right -> logIn(connection, generation, current, address);
            case Verification.RightButRehash rehash -> {
                Account account = accounts.replacePassword(current.id(), stored, rehash.fresh())
                        ? current.withPassword(rehash.fresh())
                        : current;
                yield logIn(connection, generation, account, address);
            }
        };
    }

    private static LoginResult wrongAnswer(Attempts afterThisOne) {
        return afterThisOne.exhausted()
                ? new LoginResult.TooManyAttempts()
                : new LoginResult.Wrong(afterThisOne.left());
    }

    private LoginResult logIn(UUID connection, long generation, Account account, String address) {
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        Instant now = clock.instant();
        accounts.recordLogin(account.id(), now, address);
        Account updated = account.loggedIn(now, address);
        return sessions.update(
                        connection,
                        generation,
                        state -> state instanceof AuthState.Registered
                                ? new AuthState.Authenticated(updated, AuthMethod.PASSWORD)
                                : state)
                .<LoginResult>map(state -> new LoginResult.LoggedIn(updated))
                .orElseGet(LoginResult.Gone::new);
    }
}
