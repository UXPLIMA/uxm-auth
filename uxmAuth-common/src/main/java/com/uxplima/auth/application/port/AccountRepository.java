package com.uxplima.auth.application.port;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * Where accounts are kept.
 *
 * <p>Every method blocks on the database. It is called from the hashing lane or the async scheduler, never
 * from a region thread or the proxy's event loop.
 */
public interface AccountRepository {

    /** The account under this name, in whatever case it was registered. */
    Optional<Account> findByName(PlayerName name);

    Optional<Account> findById(UUID id);

    /**
     * Stores a new account. Two registrations of one name at the same moment leave one row: the database
     * decides, and the loser is told who won.
     */
    Insert insert(Account account);

    /** Writes every field of an account that exists. */
    void update(Account account);

    /** How many accounts were registered from this address, for the accounts-per-address limit. */
    int countRegisteredFrom(String address);

    /** What an insert found. */
    sealed interface Insert permits Inserted, NameTaken {}

    /** The account is stored. */
    record Inserted() implements Insert {}

    /** Somebody holds the name already, written as they registered it. */
    record NameTaken(PlayerName stored) implements Insert {
        public NameTaken {
            Objects.requireNonNull(stored, "stored");
        }
    }
}
