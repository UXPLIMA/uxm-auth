package com.uxplima.auth.application.staff;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.BreachedPasswords;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.premium.UuidScheme;

/**
 * Staff make a password account under a name, held to the same name and password rules a player is. The account
 * takes the id its player plays under: the one they are online with, or the id the server derives from the name.
 */
public final class ForceRegister {

    /** What making the account came to. */
    public sealed interface Outcome {
        /** The account is made. */
        record Made(Account account) implements Outcome {
            public Made {
                Objects.requireNonNull(account, "account");
            }
        }

        /** An account holds the name already. */
        record Taken() implements Outcome {}

        /** The name rules of config.conf refuse the name. */
        record NameRefused() implements Outcome {}

        /** The password rules refuse the password, for this reason. */
        record Rejected(PasswordVerdict verdict) implements Outcome {
            public Rejected {
                Objects.requireNonNull(verdict, "verdict");
            }
        }
    }

    private final AccountRepository accounts;
    private final Hashing hashing;
    private final Supplier<NamePolicy> names;
    private final Supplier<PasswordPolicy> policy;
    private final BreachedPasswords breaches;
    private final Clock clock;

    public ForceRegister(
            AccountRepository accounts,
            Hashing hashing,
            Supplier<NamePolicy> names,
            Supplier<PasswordPolicy> policy,
            BreachedPasswords breaches,
            Clock clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.hashing = Objects.requireNonNull(hashing, "hashing");
        this.names = Objects.requireNonNull(names, "names");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.breaches = Objects.requireNonNull(breaches, "breaches");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Makes the account. Hashes and writes the table, so it runs on the lane.
     *
     * @param online the id of the player online under the name on this server, when there is one
     */
    public Outcome register(String name, Password password, Optional<UUID> online) {
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(online, "online");
        if (!(names.get().check(Objects.requireNonNull(name, "name")) instanceof NameVerdict.Accepted accepted)) {
            return new Outcome.NameRefused();
        }
        PlayerName player = accepted.name();
        if (accounts.findByName(player).isPresent()) {
            return new Outcome.Taken();
        }
        PasswordVerdict verdict = policy.get().check(password);
        if (!(verdict instanceof PasswordVerdict.Accepted)) {
            return new Outcome.Rejected(verdict);
        }
        if (breaches.breached(password)) {
            return new Outcome.Rejected(new PasswordVerdict.Breached());
        }
        Instant now = clock.instant();
        UUID id = online.orElseGet(() -> UuidScheme.offline(player));
        // Made by staff, from no address of the player's: the account counts against none.
        Account made = new Account(id, player, AccountKind.CRACKED, null, hashing.hash(password), now, "", null, null);
        return accounts.insert(made) instanceof AccountRepository.NameTaken
                ? new Outcome.Taken()
                : new Outcome.Made(made);
    }
}
