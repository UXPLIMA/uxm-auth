package com.uxplima.auth.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
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

    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy,
            NetworkEvents events) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Both passwords are wiped once the answer is known, whatever it is. */
    public CompletableFuture<ChangeResult> run(UUID connection, Password old, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(old, "old");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, old, fresh), old, fresh);
    }

    /**
     * Replaces the password of a logged-in player on the policy alone, for a server that turned off asking
     * for the old one. The new password is wiped once the answer is known.
     */
    public CompletableFuture<ChangeResult> replace(UUID connection, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, null, fresh), fresh);
    }

    private CompletableFuture<ChangeResult> attempt(UUID connection, @Nullable Password old, Password fresh) {
        Sessions.Held held = sessions.held(connection).orElse(null);
        if (held == null) {
            return CompletableFuture.completedFuture(new ChangeResult.Gone());
        }
        if (!(held.state() instanceof AuthState.Authenticated authenticated)) {
            return CompletableFuture.completedFuture(new ChangeResult.NotLoggedIn());
        }
        PasswordVerdict verdict = policy.get().check(fresh);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return CompletableFuture.completedFuture(new ChangeResult.Rejected(verdict));
        }
        long generation = held.generation();
        UUID id = authenticated.account().id();
        return lane.submit(() -> change(connection, generation, id, old, fresh));
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
        PasswordHash hash = hashing.hash(fresh);
        if (!sessions.isOpen(connection, generation)) {
            return new ChangeResult.Gone();
        }
        if (!accounts.replacePassword(id, stored, hash)) {
            // Changed on another server between the read and this write. That change stands.
            return new ChangeResult.WrongOld();
        }
        Account updated = current.withPassword(hash);
        sessions.update(
                connection,
                generation,
                state -> state instanceof AuthState.Authenticated authenticated
                        ? new AuthState.Authenticated(updated, authenticated.method())
                        : state);
        // The password ends every session of the account on every server, but the one that changed it.
        // A visit that cannot be named keeps nothing: every session ends, this one too.
        Optional<String> keep = sessions.held(connection)
                .map(Sessions.Held::visit)
                .filter(visit -> Bulletin.VISIT.matcher(visit).matches());
        events.publish(new Bulletin.SessionsEnded(id, keep));
        return new ChangeResult.Changed();
    }

    private boolean wrong(Password old, PasswordHash stored) {
        return hashing.verify(old, stored) instanceof Verification.Wrong;
    }
}
