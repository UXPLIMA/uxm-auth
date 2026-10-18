package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;
import org.jspecify.annotations.Nullable;

/**
 * Names a cracked account holds and a paid owner proved, held for staff to settle, where the operator chose
 * that both are stopped until then. Every method blocks on the database.
 */
public interface Disputes {

    /** Holds the name for staff. A name already held keeps its first dispute. */
    void raise(PlayerName name, UUID premiumId, Instant at);

    /** The dispute over this name, in whatever case. */
    Optional<Dispute> of(PlayerName name);

    /** Staff settled the name. */
    void resolve(PlayerName name);

    /** Staff settled the dispute under this name for the cracked account that holds it now, which keeps it. */
    void keep(PlayerName name, UUID holder);

    /**
     * Who proved the name, and when.
     *
     * @param keptFor the cracked account staff settled it for, which keeps the name; empty while it is unsettled
     */
    record Dispute(
            UUID premiumId, Instant raisedAt, @Nullable UUID keptFor) {
        public Dispute {
            Objects.requireNonNull(premiumId, "premiumId");
            Objects.requireNonNull(raisedAt, "raisedAt");
        }

        /** A dispute nobody has settled. */
        public Dispute(UUID premiumId, Instant raisedAt) {
            this(premiumId, raisedAt, null);
        }

        /** Whether staff settled it for a cracked holder. */
        public boolean kept() {
            return keptFor != null;
        }
    }
}
