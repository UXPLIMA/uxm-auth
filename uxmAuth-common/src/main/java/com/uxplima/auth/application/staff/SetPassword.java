package com.uxplima.auth.application.staff;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;

/**
 * Staff set an account's password, under the same rules a player's own is held to. The old one stops working at
 * once: every session of the account ends on every member, and every remembered one is forgotten.
 */
public final class SetPassword {

    /** What setting came to. */
    public sealed interface Outcome {
        /** The password is set. */
        record Set() implements Outcome {}

        /** The rules refused it, for this reason. */
        record Rejected(PasswordVerdict verdict) implements Outcome {
            public Rejected {
                Objects.requireNonNull(verdict, "verdict");
            }
        }

        /** The password changed elsewhere between the read and the write; that change stands. */
        record ChangedMeanwhile() implements Outcome {}

        /** No account is under the name. */
        record Unknown() implements Outcome {}
    }

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final Supplier<PasswordPolicy> policy;
    private final BreachedPasswords breaches;
    private final RememberedSessions remembered;
    private final NetworkEvents events;

    public SetPassword(
            AccountRepository accounts,
            Hashing hashing,
            Supplier<PasswordPolicy> policy,
            BreachedPasswords breaches,
            RememberedSessions remembered,
            NetworkEvents events) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.breaches = Objects.requireNonNull(breaches, "breaches");
        this.remembered = Objects.requireNonNull(remembered, "remembered");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Sets the password of the account under this name. Hashes and writes the table, so it runs on the lane. */
    public Outcome set(PlayerName name, Password fresh) {
        Objects.requireNonNull(fresh, "fresh");
        Optional<Account> account = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (account.isEmpty()) {
            return new Outcome.Unknown();
        }
        PasswordVerdict verdict = policy.get().check(fresh);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return new Outcome.Rejected(verdict);
        }
        if (breaches.breached(fresh)) {
            return new Outcome.Rejected(new PasswordVerdict.Breached());
        }
        Account found = account.get();
        if (!accounts.replacePassword(found.id(), found.password(), hashing.hash(fresh))) {
            return new Outcome.ChangedMeanwhile();
        }
        remembered.forget(found.id());
        events.publish(new Bulletin.SessionsEnded(found.id(), Optional.empty()));
        return new Outcome.Set();
    }
}
