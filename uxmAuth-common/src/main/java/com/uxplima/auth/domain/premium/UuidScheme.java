package com.uxplima.auth.domain.premium;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * The UUID a player plays under. It is what every other plugin keys an inventory, a rank and a balance by, so
 * an account keeps its own under every scheme but one: under {@link #MOJANG} a cracked account that proves a
 * paid name moves to Mojang's.
 */
public enum UuidScheme {

    /** A new premium player gets Mojang's, a new cracked one the offline one, and an account keeps its first. */
    KEEP_FIRST {
        @Override
        public UUID idFor(PlayerName name, Optional<UUID> mojangId, Optional<Account> existing) {
            return existing.map(Account::id).or(() -> mojangId).orElseGet(() -> offline(name));
        }
    },

    /** Everybody gets the offline one, derived from the name. */
    OFFLINE {
        @Override
        public UUID idFor(PlayerName name, Optional<UUID> mojangId, Optional<Account> existing) {
            return existing.map(Account::id).orElseGet(() -> offline(name));
        }
    },

    /** Every premium player gets Mojang's; a cracked player the offline one. */
    MOJANG {
        @Override
        public UUID idFor(PlayerName name, Optional<UUID> mojangId, Optional<Account> existing) {
            return mojangId.or(() -> existing.map(Account::id)).orElseGet(() -> offline(name));
        }
    },

    /** Every new player gets a fresh one at random, kept from then on. */
    RANDOM {
        @Override
        public UUID idFor(PlayerName name, Optional<UUID> mojangId, Optional<Account> existing) {
            return existing.map(Account::id).orElseGet(UUID::randomUUID);
        }
    };

    /**
     * The UUID this player plays under.
     *
     * @param mojangId the id Mojang's handshake proved, or empty for a cracked connection
     * @param existing the account that holds the name already, if one does
     */
    public abstract UUID idFor(PlayerName name, Optional<UUID> mojangId, Optional<Account> existing);

    /** The word config.conf names this scheme by. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The UUID an offline server derives from a name, the same way the server itself does. */
    public static UUID offline(PlayerName name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name.value()).getBytes(StandardCharsets.UTF_8));
    }
}
