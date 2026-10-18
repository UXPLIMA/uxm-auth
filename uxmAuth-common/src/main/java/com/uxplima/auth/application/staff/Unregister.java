package com.uxplima.auth.application.staff;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.network.Bulletin;

/**
 * Staff remove an account: the account and everything kept under it are deleted, and every member hears it, so
 * its player is sent away on whichever server or proxy they stand.
 */
public final class Unregister {

    /** What removing came to. */
    public enum Removed {
        /** The account is gone. */
        REMOVED,
        /** No account is under the name. */
        UNKNOWN
    }

    private final AccountRepository accounts;
    private final NetworkEvents events;

    public Unregister(AccountRepository accounts, NetworkEvents events) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.events = Objects.requireNonNull(events, "events");
    }

    /** Removes the account under this name. Writes the tables. */
    public Removed remove(PlayerName name) {
        Optional<Account> account = accounts.findByName(Objects.requireNonNull(name, "name"));
        if (account.isEmpty()) {
            return Removed.UNKNOWN;
        }
        accounts.delete(account.get().id());
        events.publish(new Bulletin.Unregistered(account.get().id()));
        return Removed.REMOVED;
    }
}
