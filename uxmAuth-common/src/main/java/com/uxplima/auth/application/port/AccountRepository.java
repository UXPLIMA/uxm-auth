package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

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

    /** Writes every field of an account that exists. For staff tools, which mean every field. */
    void update(Account account);

    /** Records a login, and touches nothing else on the row. */
    void recordLogin(UUID id, Instant at, String address);

    /**
     * Replaces the password only if the row still holds {@code expected}. A password changed on another server
     * between the read and this write is kept, and the answer is false.
     */
    boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh);

    /**
     * How many password accounts were registered from this address since this moment, for the accounts-per-address
     * limit. An IPv6 address counts with its /64; premium and Bedrock accounts are not counted.
     */
    int countRegisteredFrom(String address, Instant since);

    /** The premium account that proved this Mojang id, under whatever name it holds now. */
    Optional<Account> findByPremiumId(UUID premiumId);

    /**
     * Moves an account to another name and keeps its id, so everything keyed by the id stays where it is. The old
     * name is remembered with where it went. A name somebody holds is refused, and nothing moves.
     */
    Insert rename(UUID id, PlayerName to, Instant at);

    /** The name the account last moved away from this name holds now, if one did. */
    Optional<PlayerName> renamedFrom(PlayerName old);

    /** The latest move of this account its player has not been told of yet. */
    Optional<Renamed> untoldRename(UUID id);

    /** The player of this account has been told of its moves. */
    void markRenameTold(UUID id);

    /** Removes an account and what is remembered of its moves. */
    void delete(UUID id);

    /** Gives an account another id, as a scheme that follows Mojang's does when a cracked account turns premium. */
    void moveId(UUID from, UUID to);

    /** An account moved from one name to another. */
    record Renamed(PlayerName from, PlayerName to) {
        public Renamed {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(to, "to");
        }
    }

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
