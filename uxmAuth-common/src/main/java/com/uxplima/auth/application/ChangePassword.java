package com.uxplima.auth.application;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.AuthState;

/** Replaces the password of a logged-in player who can still type the old one. */
public final class ChangePassword {

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final HashingLane lane;
    private final Sessions sessions;
    private final Supplier<PasswordPolicy> policy;

    public ChangePassword(
            AccountRepository accounts,
            Hashing hashing,
            HashingLane lane,
            Sessions sessions,
            Supplier<PasswordPolicy> policy) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.lane = Objects.requireNonNull(lane, "lane");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** Both passwords are wiped once the answer is known, whatever it is. */
    public CompletableFuture<ChangeResult> run(UUID connection, Password old, Password fresh) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(old, "old");
        Objects.requireNonNull(fresh, "fresh");
        return Wiping.after(() -> attempt(connection, old, fresh), old, fresh);
    }

    private CompletableFuture<ChangeResult> attempt(UUID connection, Password old, Password fresh) {
        AuthState state = sessions.state(connection).orElse(null);
        if (state == null) {
            return CompletableFuture.completedFuture(new ChangeResult.Gone());
        }
        if (!(state instanceof AuthState.Authenticated authenticated)) {
            return CompletableFuture.completedFuture(new ChangeResult.NotLoggedIn());
        }
        PasswordVerdict verdict = policy.get().check(fresh);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return CompletableFuture.completedFuture(new ChangeResult.Rejected(verdict));
        }
        PasswordHash stored = authenticated.account().password();
        if (stored == null) {
            return CompletableFuture.completedFuture(new ChangeResult.WrongOld());
        }
        return lane.submit(() -> hashing.verify(old, stored) instanceof Verification.Wrong
                ? new ChangeResult.WrongOld()
                : store(connection, authenticated.account(), hashing.hash(fresh)));
    }

    private ChangeResult store(UUID connection, Account account, PasswordHash hash) {
        if (!sessions.isOpen(connection)) {
            return new ChangeResult.Gone();
        }
        Account updated = account.withPassword(hash);
        accounts.update(updated);
        sessions.update(
                connection,
                current -> current instanceof AuthState.Authenticated authenticated
                        ? new AuthState.Authenticated(updated, authenticated.method())
                        : current);
        return new ChangeResult.Changed();
    }
}
