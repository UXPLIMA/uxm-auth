package com.uxplima.auth.application;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import org.jspecify.annotations.Nullable;

/**
 * Replaces the password of a logged-in player who can still type the old one. A wrong old password costs what a
 * wrong login does: it counts against the address it came from and against the account, so the command is no
 * oracle for the password and no way to make the server hash without end.
 */
public final class ChangePassword {

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Supplier<PasswordPolicy> policy;
    private final NetworkEvents events;
    private final RememberedSessions remembered;
    private final BreachedPasswords breaches;
    private final AuthEvents listeners;
    private final LogIn.Failures failures;
    private final LogIn.Lockout lockout;

    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            NetworkEvents events,
            RememberedSessions remembered) {
        this(accounts, hashing, lane, sessions, policy, events, remembered, BreachedPasswords.NONE);
    }

    /** @param breaches whether a new password is known from a breach, asked on the lane before the hash */
    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            NetworkEvents events,
            RememberedSessions remembered,
            BreachedPasswords breaches) {
        this(accounts, hashing, lane, sessions, policy, events, remembered, breaches, AuthEvents.NONE);
    }

    /** @param listeners told of every change made */
    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            NetworkEvents events,
            RememberedSessions remembered,
            BreachedPasswords breaches,
            AuthEvents listeners) {
        this(
                accounts,
                hashing,
                lane,
                sessions,
                policy,
                events,
                remembered,
                breaches,
                listeners,
                (address, host) -> Optional.empty(),
                LogIn.Lockout.NONE);
    }

    /**
     * @param failures where a wrong old password counts against its address
     * @param lockout where a wrong old password counts against the account, which it locks after enough
     */
    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            NetworkEvents events,
            RememberedSessions remembered,
            BreachedPasswords breaches,
            AuthEvents listeners,
            LogIn.Failures failures,
            LogIn.Lockout lockout) {
        this.failures = Objects.requireNonNull(failures, "failures");
        this.lockout = Objects.requireNonNull(lockout, "lockout");
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.events = Objects.requireNonNull(events, "events");
        this.remembered = Objects.requireNonNull(remembered, "remembered");
        this.breaches = Objects.requireNonNull(breaches, "breaches");
    }

    /**
     * Both passwords are wiped once the answer is known, whatever it is. A wrong old one counts against this
     * address, through the host the session came by.
     */
    public CompletableFuture<ChangeResult> run(UUID connection, Password old, Password fresh, String address) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(old, "old");
        Objects.requireNonNull(fresh, "fresh");
        Objects.requireNonNull(address, "address");
        return Wiping.after(() -> attempt(connection, old, fresh, false, address), old, fresh);
    }

    /**
     * Replaces the password of a logged-in player on the policy alone, for a server that turned off asking
     * for the old one. The new password is wiped once the answer is known.
     */
    public CompletableFuture<ChangeResult> replace(UUID connection, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, null, fresh, false, ""), fresh);
    }

    /**
     * Replaces the weak password a login was held up by. It asks no old password, so it is taken only from a
     * player held for it: anyone else answers {@link ChangeResult.NotLoggedIn}.
     */
    public CompletableFuture<ChangeResult> replaceWeak(UUID connection, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, null, fresh, true, ""), fresh);
    }

    private CompletableFuture<ChangeResult> attempt(
            UUID connection, @Nullable Password old, Password fresh, boolean heldOnly, String address) {
        Sessions.Held held = sessions.held(connection).orElse(null);
        if (held == null) {
            return CompletableFuture.completedFuture(new ChangeResult.Gone());
        }
        Optional<UUID> owner =
                heldOnly && !(held.state() instanceof AuthState.Replacing) ? Optional.empty() : owner(held.state());
        if (owner.isEmpty()) {
            return CompletableFuture.completedFuture(new ChangeResult.NotLoggedIn());
        }
        PasswordVerdict verdict = policy.get().check(fresh);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return CompletableFuture.completedFuture(new ChangeResult.Rejected(verdict));
        }
        long generation = held.generation();
        UUID id = owner.get();
        if (old == null) {
            PasswordHash proven = proven(held.state());
            return lane.submit(
                    () -> change(connection, generation, id, null, fresh, proven, ChangeResult.WrongOld::new));
        }
        String host = held.host();
        return lane.submit(() -> checkedOld(connection, generation, id, old, fresh, address, host));
    }

    /**
     * Takes a try at the account before the old password is hashed, unless the account is locked; a wrong old
     * password keeps the try and counts against the address, a right one gives it back.
     */
    private ChangeResult checkedOld(
            UUID connection, long generation, UUID id, Password old, Password fresh, String address, String host) {
        LogIn.Lockout.Try.Taken taken;
        switch (lockout.take(id)) {
            case LogIn.Lockout.Try.Refused refused -> {
                return new ChangeResult.Locked(refused.left());
            }
            case LogIn.Lockout.Try.Taken took -> taken = took;
        }
        AtomicBoolean wrong = new AtomicBoolean();
        ChangeResult result = change(connection, generation, id, old, fresh, null, () -> {
            wrong.set(true);
            return wrongOld(address, host, taken);
        });
        if (!wrong.get()) {
            lockout.right(taken);
        }
        return result;
    }

    /** A wrong old password: the address may be banned by it now, or the account locked. */
    private ChangeResult wrongOld(String address, String host, LogIn.Lockout.Try.Taken taken) {
        Optional<Duration> banned = failures.failed(address, host);
        if (banned.isPresent()) {
            return new ChangeResult.Banned(banned.get());
        }
        return taken.locks().<ChangeResult>map(ChangeResult.Locked::new).orElseGet(ChangeResult.WrongOld::new);
    }

    /**
     * The hash a player held to replace a weak password proved at the login, which a change that asks no old
     * password must still find in the row; nothing for anyone else.
     */
    private static @Nullable PasswordHash proven(AuthState state) {
        return state instanceof AuthState.Replacing replacing
                ? replacing.account().password()
                : null;
    }

    /** The account a logged-in player, or one held to replace a weak password, may change the password of. */
    private static Optional<UUID> owner(AuthState state) {
        return switch (state) {
            case AuthState.Authenticated in -> Optional.of(in.account().id());
            case AuthState.Replacing replacing ->
                Optional.of(replacing.account().id());
            case AuthState.Registered waiting -> Optional.empty();
            case AuthState.SecondFactor waiting -> Optional.empty();
            case AuthState.SettingUp held -> Optional.empty();
            case AuthState.Unregistered nobody -> Optional.empty();
        };
    }

    /**
     * The old password is checked against the row as it is now, and the new one replaces exactly that row's
     * value. A change made on another server in between wins, and this one answers that the old password
     * was wrong, which by then it is.
     *
     * <p>A player held to replace a weak password types no old one, so the new one replaces only the hash they
     * proved at the login. When staff or another server changed it since, that change stands and the player is
     * told to log in again, rather than the fresh row being taken as theirs to overwrite.
     */
    private ChangeResult change(
            UUID connection,
            long generation,
            UUID id,
            @Nullable Password old,
            Password fresh,
            @Nullable PasswordHash proven,
            Supplier<ChangeResult> whenWrong) {
        if (!sessions.isOpen(connection, generation)) {
            return new ChangeResult.Gone();
        }
        Account current = accounts.findById(id).orElse(null);
        PasswordHash stored = current == null ? null : current.password();
        if (current == null) {
            return new ChangeResult.WrongOld();
        }
        if (old != null && (stored == null || wrong(old, stored))) {
            return whenWrong.get();
        }
        boolean pinned = old == null && proven != null;
        if (old == null && proven != null && !proven.equals(stored)) {
            return new ChangeResult.ChangedElsewhere();
        }
        if (breaches.breached(fresh)) {
            return new ChangeResult.Rejected(new PasswordVerdict.Breached());
        }
        PasswordHash hash = hashing.hash(fresh);
        if (!sessions.isOpen(connection, generation)) {
            return new ChangeResult.Gone();
        }
        if (!accounts.replacePassword(id, stored, hash)) {
            // Changed on another server between the read and this write. That change stands.
            return pinned ? new ChangeResult.ChangedElsewhere() : new ChangeResult.WrongOld();
        }
        boolean replaced = loggedIn(connection, generation, current.withPassword(hash));
        endOtherSessions(connection, id);
        listeners.passwordChanged(id, current.name());
        return replaced ? new ChangeResult.Replaced() : new ChangeResult.Changed();
    }

    /**
     * The session carries the new hash, and a player held to replace a weak password is now in. Answers whether
     * they were held.
     */
    private boolean loggedIn(UUID connection, long generation, Account updated) {
        AtomicBoolean replaced = new AtomicBoolean();
        sessions.update(connection, generation, state -> switch (state) {
            case AuthState.Authenticated in -> new AuthState.Authenticated(updated, in.method());
            case AuthState.Replacing replacing -> {
                replaced.set(true);
                yield new AuthState.Authenticated(updated, AuthMethod.PASSWORD);
            }
            default -> state;
        });
        return replaced.get();
    }

    /**
     * The password ends every session of the account on every server, and every remembered one, but the one that
     * changed it. A visit that cannot be named keeps nothing: every session ends, this one too.
     */
    private void endOtherSessions(UUID connection, UUID id) {
        Optional<String> keep = sessions.held(connection)
                .map(Sessions.Held::visit)
                .filter(visit -> Bulletin.VISIT.matcher(visit).matches());
        remembered.forget(id);
        events.publish(new Bulletin.SessionsEnded(id, keep));
    }

    private boolean wrong(Password old, PasswordHash stored) {
        return hashing.verify(old, stored) instanceof Verification.Wrong;
    }
}
