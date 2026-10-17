package com.uxplima.auth.application;

import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;

/**
 * Reads the name a connection arrives under: the operator's rules first, then the case the name was registered
 * in, then what the connection waits for.
 *
 * <p>Once {@code Notch} holds an account, {@code notch} is refused with the right spelling rather than let in
 * under a name that only looks like the owner's. This runs on the thread the server gives the pre-login, which
 * may wait on the database.
 */
public final class ReadTheName {

    /** What the door makes of a name. */
    public sealed interface Reading {

        /** The name is refused, for the reason the verdict gives. */
        record Refused(NameVerdict verdict) implements Reading {
            public Refused {
                Objects.requireNonNull(verdict, "verdict");
            }
        }

        /** The name may come in, and this is what it waits for. */
        record Opens(AuthState state) implements Reading {
            public Opens {
                Objects.requireNonNull(state, "state");
            }
        }
    }

    private final AccountRepository accounts;
    private final Supplier<NamePolicy> policy;
    private final IntSupplier maxAttempts;

    public ReadTheName(AccountRepository accounts, Supplier<NamePolicy> policy, IntSupplier maxAttempts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.maxAttempts = Objects.requireNonNull(maxAttempts, "maxAttempts");
    }

    /** What a Java connection under this name opens with, or why it is refused. */
    public Reading read(String raw) {
        Objects.requireNonNull(raw, "raw");
        NameVerdict verdict = policy.get().check(raw);
        if (!(verdict instanceof NameVerdict.Accepted accepted)) {
            return new Reading.Refused(verdict);
        }
        Optional<Account> account = accounts.findByName(accepted.name());
        if (account.isPresent() && !account.get().name().value().equals(raw)) {
            return new Reading.Refused(new NameVerdict.WrongCase(account.get().name()));
        }
        return new Reading.Opens(waitingFor(accepted.name(), account));
    }

    /**
     * Why a Bedrock name is refused, if it is. Its prefix, its spaces and its length are Xbox's and Floodgate's,
     * so only the blocked words hold.
     */
    public Optional<NameVerdict> readBedrock(String raw) {
        return policy.get().blockedIn(Objects.requireNonNull(raw, "raw")).map(NameVerdict.Blocked::new);
    }

    /** What a connection under a name the door already let through waits for: its password, or an account. */
    public AuthState waiting(PlayerName name) {
        Objects.requireNonNull(name, "name");
        return waitingFor(name, accounts.findByName(name));
    }

    private AuthState waitingFor(PlayerName name, Optional<Account> account) {
        return account.<AuthState>map(found -> new AuthState.Registered(found, Attempts.fresh(maxAttempts.getAsInt())))
                .orElseGet(() -> new AuthState.Unregistered(name));
    }
}
