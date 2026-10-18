package com.uxplima.auth.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
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

/** Replaces the password of a logged-in player who can still type the old one. */
public final class ChangePassword {

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Supplier<PasswordPolicy> policy;
    private final NetworkEvents events;
    private final RememberedSessions remembered;
    private final BreachedPasswords breaches;

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
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.events = Objects.requireNonNull(events, "events");
        this.remembered = Objects.requireNonNull(remembered, "remembered");
        this.breaches = Objects.requireNonNull(breaches, "breaches");
    }

    /** Both passwords are wiped once the answer is known, whatever it is. */
    public CompletableFuture<ChangeResult> run(UUID connection, Password old, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(old, "old");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, old, fresh, false), old, fresh);
    }

    /**
     * Replaces the password of a logged-in player on the policy alone, for a server that turned off asking
     * for the old one. The new password is wiped once the answer is known.
     */
    public CompletableFuture<ChangeResult> replace(UUID connection, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, null, fresh, false), fresh);
    }

    /**
     * Replaces the weak password a login was held up by. It asks no old password, so it is taken only from a
     * player held for it: anyone else answers {@link ChangeResult.NotLoggedIn}.
     */
    public CompletableFuture<ChangeResult> replaceWeak(UUID connection, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, null, fresh, true), fresh);
    }

    private CompletableFuture<ChangeResult> attempt(
            UUID connection, @Nullable Password old, Password fresh, boolean heldOnly) {
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
        return lane.submit(() -> change(connection, generation, id, old, fresh));
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
     */
    private ChangeResult change(UUID connection, long generation, UUID id, @Nullable Password old, Password fresh) {
        if (!sessions.isOpen(connection, generation)) {
            return new ChangeResult.Gone();
        }
        Account current = accounts.findById(id).orElse(null);
        PasswordHash stored = current == null ? null : current.password();
        if (current == null || (old != null && (stored == null || wrong(old, stored)))) {
            return new ChangeResult.WrongOld();
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
            return new ChangeResult.WrongOld();
        }
        boolean replaced = loggedIn(connection, generation, current.withPassword(hash));
        endOtherSessions(connection, id);
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
