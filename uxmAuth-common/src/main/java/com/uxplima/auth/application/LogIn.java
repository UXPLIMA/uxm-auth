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

import com.uxplima.auth.application.factor.SecondStep;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.protection.AddressTally;
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
 *
 * <p>Beside the attempts of the connection and the bans of the address, every try counts against the account
 * itself, wherever it came from: a locked account checks no password until its lock runs out, and says so
 * whether the password would have been right or not.
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

    /**
     * Where a try at an account's password or code counts against the account, from any address, and the short
     * lock enough wrong ones earn. A try is counted as wrong before it is checked, so tries sent together cannot
     * pass the limit; a right one gives it back, and a finished login starts the count again.
     */
    public interface Lockout {
        /** No lock: nothing counts, nothing waits. */
        Lockout NONE = new Lockout() {
            @Override
            public Try take(Account account, String address, String host) {
                return take(account.id());
            }

            @Override
            public Try take(UUID account) {
                return new Try.Taken(account, AddressTally.clean(), AddressTally.clean(), Instant.EPOCH);
            }

            @Override
            public void right(Try.Taken taken) {}

            @Override
            public void passed(UUID account) {}
        };

        /** What a try came to before anything was checked. */
        sealed interface Try {
            /** The account is locked for this long; nothing was counted or checked. */
            record Refused(Duration left) implements Try {
                public Refused {
                    Objects.requireNonNull(left, "left");
                }
            }

            /** The try is counted as wrong: the tally before it, after it, and when it was taken. */
            record Taken(UUID account, AddressTally before, AddressTally after, Instant at) implements Try {
                public Taken {
                    Objects.requireNonNull(account, "account");
                    Objects.requireNonNull(before, "before");
                    Objects.requireNonNull(after, "after");
                    Objects.requireNonNull(at, "at");
                }

                /** How long the account is locked when this try is what locked it; nothing otherwise. */
                public Optional<Duration> locks() {
                    return after.bannedAt(at) && !before.bannedAt(at)
                            ? Optional.of(after.leftAt(at))
                            : Optional.empty();
                }
            }
        }

        /**
         * Takes a try at the account from this address through this host. The address its owner last logged in
         * from, unless a filter shares it, is not refused by a lock; its tries still count.
         */
        Try take(Account account, String address, String host);

        /** Takes a try at the account from a player logged in to it, whom no address lets past a lock. */
        Try take(UUID account);

        /** The try was right: its count is given back. */
        void right(Try.Taken taken);

        /** The owner finished a login: the account's count starts again. */
        void passed(UUID account);
    }

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Clock clock;
    private final Failures failures;
    private final Supplier<PasswordPolicy> policy;
    private final Captcha captcha;
    private final SecondStep secondStep;
    private final AuthEvents events;
    private final Lockout lockout;

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
        this(accounts, hashing, lane, sessions, clock, failures, policy, captcha, SecondStep.NONE);
    }

    /** @param secondStep whether a right password leads to a code from the app before the login is whole */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures,
            Supplier<PasswordPolicy> policy,
            Captcha captcha,
            SecondStep secondStep) {
        this(accounts, hashing, lane, sessions, clock, failures, policy, captcha, secondStep, AuthEvents.NONE);
    }

    /** @param events asked before a password is checked, and told of a wrong one */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures,
            Supplier<PasswordPolicy> policy,
            Captcha captcha,
            SecondStep secondStep,
            AuthEvents events) {
        this(accounts, hashing, lane, sessions, clock, failures, policy, captcha, secondStep, events, Lockout.NONE);
    }

    /** @param lockout where every try counts against the account it was typed for, and locks it after enough */
    public LogIn(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Clock clock,
            Failures failures,
            Supplier<PasswordPolicy> policy,
            Captcha captcha,
            SecondStep secondStep,
            AuthEvents events,
            Lockout lockout) {
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        this.events = Objects.requireNonNull(events, "events");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.failures = Objects.requireNonNull(failures, "failures");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.captcha = Objects.requireNonNull(captcha, "captcha");
        this.secondStep = Objects.requireNonNull(secondStep, "secondStep");
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

    /**
     * A connection that may answer now is first put to the listeners, off the caller's thread; a login one refuses
     * spends no attempt and checks no password.
     */
    private CompletableFuture<LoginResult> attempt(UUID connection, long opening, Password password, String address) {
        Sessions.Held held = sessions.held(connection)
                .filter(found -> opening == NO_OPENING || found.generation() == opening)
                .orElse(null);
        if (held == null) {
            return CompletableFuture.completedFuture(new LoginResult.Gone());
        }
        if (!(held.state() instanceof AuthState.Registered waiting)
                || waiting.attempts().exhausted()) {
            return CompletableFuture.completedFuture(refusal(held.state()));
        }
        if (captcha.blocks(connection, waiting.account().name())) {
            return CompletableFuture.completedFuture(new LoginResult.CaptchaNeeded());
        }
        return lane.submit(() -> events.beforeLogin(connection)
                .<LoginResult>map(LoginResult.Refused::new)
                .orElseGet(() -> spendAndCheck(connection, held, password, address)));
    }

    /**
     * Takes a try at the account, unless it is locked, then spends the attempt, unless another answer of the same
     * connection spent the last one meanwhile, and checks.
     */
    private LoginResult spendAndCheck(UUID connection, Sessions.Held held, Password password, String address) {
        Lockout.Try.Taken taken;
        if (held.state() instanceof AuthState.Registered waiting) {
            switch (lockout.take(waiting.account(), address, held.host())) {
                case Lockout.Try.Refused refused -> {
                    return new LoginResult.Locked(refused.left());
                }
                case Lockout.Try.Taken took -> taken = took;
            }
        } else {
            return refusal(held.state());
        }
        AtomicReference<AuthState.Registered> reserved = new AtomicReference<>();
        AuthState after = sessions.update(connection, held.generation(), current -> spend(current, reserved))
                .orElse(null);
        AuthState.Registered spent = reserved.get();
        if (spent == null) {
            lockout.right(taken);
            return refusal(after);
        }
        LoginResult result = check(connection, held, spent, password, address, taken);
        if (!(result instanceof LoginResult.Wrong
                || result instanceof LoginResult.TooManyAttempts
                || result instanceof LoginResult.Banned
                || result instanceof LoginResult.Locked)) {
            // Nothing was found wrong: the account's count is given back, or started again by the login.
            settle(result, taken);
        }
        return result;
    }

    /** A right password gives its try back; one that finished the login starts the account's count again. */
    private void settle(LoginResult result, Lockout.Try.Taken taken) {
        if (result instanceof LoginResult.LoggedIn || result instanceof LoginResult.MustReplace) {
            lockout.passed(taken.account());
        } else {
            lockout.right(taken);
        }
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
            case AuthState.SettingUp held -> new LoginResult.AlreadyLoggedIn();
            case AuthState.Replacing replacing -> new LoginResult.MustReplace(replacing.why());
            case AuthState.Registered waiting -> new LoginResult.TooManyAttempts();
            case AuthState.SecondFactor waiting -> new LoginResult.CodeNeeded();
        };
    }

    private LoginResult check(
            UUID connection,
            Sessions.Held held,
            AuthState.Registered spent,
            Password password,
            String address,
            Lockout.Try.Taken taken) {
        long generation = held.generation();
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        if (policy.get().refusesAtLogin(password)) {
            // No stored password can be this answer: it costs an attempt, not a read and a hash.
            captcha.failed(spent.account().name());
            return wrongAnswer(connection, spent.attempts(), address, held.host(), taken);
        }
        Account current = accounts.findById(spent.account().id()).orElse(null);
        PasswordHash stored = current == null ? null : current.password();
        if (current == null || stored == null) {
            return new LoginResult.NotRegistered();
        }
        return switch (hashing.verify(password, stored)) {
            case Verification.Wrong wrong -> {
                captcha.failed(current.name());
                yield wrongAnswer(connection, spent.attempts(), address, held.host(), taken);
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

    private LoginResult wrongAnswer(
            UUID connection, Attempts afterThisOne, String address, String host, Lockout.Try.Taken taken) {
        events.wrongPassword(connection, afterThisOne.left());
        Optional<Duration> banned = failures.failed(address, host);
        if (banned.isPresent()) {
            return new LoginResult.Banned(banned.get());
        }
        Optional<Duration> locked = taken.locks();
        if (locked.isPresent()) {
            return new LoginResult.Locked(locked.get());
        }
        return afterThisOne.exhausted()
                ? new LoginResult.TooManyAttempts()
                : new LoginResult.Wrong(afterThisOne.left());
    }

    /**
     * A right password logs in, unless the account asks a code next, or today's rules refuse the password: then
     * the player is held for the code, or to replace it. A login waiting for its code is not recorded yet.
     */
    private LoginResult logIn(UUID connection, long generation, Account account, String address, Password password) {
        if (!sessions.isOpen(connection, generation)) {
            return new LoginResult.Gone();
        }
        captcha.passed(account.name());
        PasswordVerdict today = policy.get().check(password);
        PasswordVerdict weak = today instanceof PasswordVerdict.Accepted ? null : today;
        if (secondStep.asks(account, AuthMethod.PASSWORD)) {
            AuthState waiting = secondStep.after(account, AuthMethod.PASSWORD, weak);
            return sessions.update(
                            connection, generation, state -> state instanceof AuthState.Registered ? waiting : state)
                    .<LoginResult>map(state -> new LoginResult.CodeNeeded())
                    .orElseGet(LoginResult.Gone::new);
        }
        Instant now = clock.instant();
        accounts.recordLogin(account.id(), now, address);
        Account updated = account.loggedIn(now, address);
        AuthState next = weak == null
                ? new AuthState.Authenticated(updated, AuthMethod.PASSWORD)
                : new AuthState.Replacing(updated, weak);
        LoginResult answer = weak == null ? new LoginResult.LoggedIn(updated) : new LoginResult.MustReplace(weak);
        return sessions.update(connection, generation, state -> state instanceof AuthState.Registered ? next : state)
                .map(state -> answer)
                .orElseGet(LoginResult.Gone::new);
    }
}
