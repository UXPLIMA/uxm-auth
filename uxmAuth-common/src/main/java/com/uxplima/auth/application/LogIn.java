package com.uxplima.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
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

    /** Where a wrong password counts against the address it came from, through the host the player typed. */
    @FunctionalInterface
    public interface Failures {
        /** The time the address is banned for now, if this failure or an earlier one banned it. */
        Optional<Duration> failed(String address, String host);
    }

    /**
     * Where a captcha stands before a login: whether this connection must solve one before the name it waits on
     * may try a password, and where a wrong or a right password of the name counts toward it.
     */
    public interface Captcha {
        /** No captcha: nothing waits, nothing counts. */
        Captcha NONE = new Captcha() {
            @Override
            public boolean blocks(UUID connection, PlayerName name) {
                return false;
            }

            @Override
            public void failed(PlayerName name) {}

            @Override
            public void passed(PlayerName name) {}
        };

        boolean blocks(UUID connection, PlayerName name);

        void failed(PlayerName name);

        void passed(PlayerName name);
    }

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Clock clock;
    private final Failures failures;
    private final Supplier<PasswordPolicy> policy;
    private final Captcha captcha;

    public LogIn(AccountRepository accounts, Hashing hashing, HashingLane lane, Sessions sessions, Clock clock) {
        this(accounts, hashing, lane, sessions, clock, (address, host) -> Optional.empty());
    }

    /** @param failures where each wrong password counts against its address */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures) {
        this(accounts, hashing, lane, sessions, clock, failures, () -> PasswordPolicy.ANY);
    }

    /** @param policy today's rules, which a right password must still pass or be replaced */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures,
            Supplier<PasswordPolicy> policy) {
        this(accounts, hashing, lane, sessions, clock, failures, policy, Captcha.NONE);
    }

    /** @param captcha the captcha that stands before the logins of a name that failed so often */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures,
            Supplier<PasswordPolicy> policy,
            Captcha captcha) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.captcha = Objects.requireNonNull(captcha, "captcha");
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
        if (held.state() instanceof AuthState.Registered waiting
                && captcha.blocks(connection, waiting.account().name())) {
            return CompletableFuture.completedFuture(new LoginResult.CaptchaNeeded());
        }
        AtomicReference<AuthState.Registered> reserved = new AtomicReference<>();
        AuthState after = sessions.update(connection, held.generation(), current -> spend(current, reserved))
                .orElse(null);
        AuthState.Registered spent = reserved.get();
        if (spent == null) {
            return CompletableFuture.completedFuture(refusal(after));
        }
        return lane.submit(() -> check(connection, held, spent, password, address));
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
            case AuthState.Replacing replacing -> new LoginResult.MustReplace(replacing.why());
            case AuthState.Registered waiting -> new LoginResult.TooManyAttempts();
        };
    }

    private LoginResult check(
            UUID connection, Sessions.Held held, AuthState.Registered spent, Password password, String address) {
        long generation = held.generation();
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        Account current = accounts.findById(spent.account().id()).orElse(null);
        PasswordHash stored = current == null ? null : current.password();
        if (current == null || stored == null) {
            return new LoginResult.NotRegistered();
        }
        return switch (hashing.verify(password, stored)) {
            case Verification.Wrong wrong -> {
                captcha.failed(current.name());
                yield wrongAnswer(spent.attempts(), address, held.host());
            }
            case Verification.Right right -> logIn(connection, generation, current, address, password);
            case Verification.RightButRehash rehash -> {
                Account account = accounts.replacePassword(current.id(), stored, rehash.fresh())
                        ? current.withPassword(rehash.fresh())
                        : current;
                yield logIn(connection, generation, account, address, password);
            }
        };
    }

    private LoginResult wrongAnswer(Attempts afterThisOne, String address, String host) {
        Optional<Duration> banned = failures.failed(address, host);
        if (banned.isPresent()) {
            return new LoginResult.Banned(banned.get());
        }
        return afterThisOne.exhausted()
                ? new LoginResult.TooManyAttempts()
                : new LoginResult.Wrong(afterThisOne.left());
    }

    /** A right password logs in, unless today's rules refuse it: then the player is held to replace it. */
    private LoginResult logIn(UUID connection, long generation, Account account, String address, Password password) {
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        Instant now = clock.instant();
        accounts.recordLogin(account.id(), now, address);
        captcha.passed(account.name());
        Account updated = account.loggedIn(now, address);
        PasswordVerdict today = policy.get().check(password);
        AuthState next = today instanceof PasswordVerdict.Accepted
                ? new AuthState.Authenticated(updated, AuthMethod.PASSWORD)
                : new AuthState.Replacing(updated, today);
        LoginResult answer = today instanceof PasswordVerdict.Accepted
                ? new LoginResult.LoggedIn(updated)
                : new LoginResult.MustReplace(today);
        return sessions.update(connection, generation, state -> state instanceof AuthState.Registered ? next : state)
                .map(state -> answer)
                .orElseGet(LoginResult.Gone::new);
    }
}
