package com.uxplima.auth.application.staff;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.AuthEvents;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import org.jspecify.annotations.Nullable;

/**
 * Staff turn an account premium or cracked. Premium is the conversion a paid owner's own claim makes: Mojang names
 * the paid account, and the account takes its id under the operator's scheme and keeps its password as a backup.
 * Cracked needs a password to log in with. Either way the account's player is sent to join again under the new way.
 */
public final class MarkKind {

    /** What marking came to. */
    public enum Marked {
        /** The account is the kind staff asked for now. */
        DONE,
        /** It is that kind already, or a kind this cannot change. */
        ALREADY,
        /** No paid account holds the name. */
        NOT_PAID,
        /** No source could say who holds the name. */
        NO_ANSWER,
        /** Another account holds the paid account already. */
        TAKEN,
        /** A cracked account logs in with a password, and this one has none. */
        NO_PASSWORD,
        /** No account is under the name. */
        UNKNOWN
    }

    private final AccountRepository accounts;
    private final Function<PlayerName, PremiumAnswer> lookup;
    private final UuidScheme scheme;
    private final NetworkEvents events;
    private final AuthEvents listeners;

    /** @param lookup asks the operator's sources who holds a name; it may go to the web */
    public MarkKind(
            AccountRepository accounts,
            Function<PlayerName, PremiumAnswer> lookup,
            UuidScheme scheme,
            NetworkEvents events) {
        this(accounts, lookup, scheme, events, AuthEvents.NONE);
    }

    /** @param listeners told of every kind staff switched */
    public MarkKind(
            AccountRepository accounts,
            Function<PlayerName, PremiumAnswer> lookup,
            UuidScheme scheme,
            NetworkEvents events,
            AuthEvents listeners) {
        this.listeners = Objects.requireNonNull(listeners, "listeners");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.lookup = Objects.requireNonNull(lookup, "lookup");
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Turns the cracked account under this name premium. Reads the web and writes the table: off any server thread. */
    public Marked premium(PlayerName name) {
        Optional<Account> found = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (found.isEmpty()) {
            return Marked.UNKNOWN;
        }
        Account account = found.get();
        if (account.kind() != AccountKind.CRACKED) {
            return Marked.ALREADY;
        }
        return switch (lookup.apply(account.name())) {
            case PremiumAnswer.Premium paid -> turnPremium(account, paid.mojangId());
            case PremiumAnswer.Cracked none -> Marked.NOT_PAID;
            case PremiumAnswer.Unknown unknown -> Marked.NO_ANSWER;
        };
    }

    /** Turns the premium account under this name cracked. Writes the table. */
    public Marked cracked(PlayerName name) {
        Optional<Account> found = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (found.isEmpty()) {
            return Marked.UNKNOWN;
        }
        Account account = found.get();
        if (account.kind() != AccountKind.PREMIUM) {
            return Marked.ALREADY;
        }
        if (account.password() == null) {
            return Marked.NO_PASSWORD;
        }
        accounts.update(withKind(account, account.id(), AccountKind.CRACKED, null));
        events.publish(new Bulletin.Rejoin(account.id()));
        listeners.kindSwitched(account.id(), account.name(), AccountKind.CRACKED);
        return Marked.DONE;
    }

    private Marked turnPremium(Account account, UUID mojangId) {
        if (accounts.findByPremiumId(mojangId).isPresent()) {
            return Marked.TAKEN;
        }
        UUID id = scheme.idFor(account.name(), Optional.of(mojangId), Optional.of(account));
        if (!id.equals(account.id())) {
            accounts.moveId(account.id(), id);
        }
        accounts.update(withKind(account, id, AccountKind.PREMIUM, mojangId));
        events.publish(new Bulletin.Rejoin(account.id()));
        listeners.kindSwitched(id, account.name(), AccountKind.PREMIUM);
        return Marked.DONE;
    }

    private static Account withKind(Account account, UUID id, AccountKind kind, @Nullable UUID premiumId) {
        return new Account(
                id,
                account.name(),
                kind,
                premiumId,
                account.password(),
                account.registeredAt(),
                account.registrationAddress(),
                account.lastLoginAt(),
                account.lastAddress());
    }
}
