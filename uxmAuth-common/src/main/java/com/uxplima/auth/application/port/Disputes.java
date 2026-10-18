package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.name.PlayerName;

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

    /** Staff settled the dispute under this name for the cracked holder, who keeps the name. */
    void keep(PlayerName name);

    /**
     * Who proved the name, and when.
     *
     * @param kept staff settled it for the cracked holder: it stops nobody, and the paid owner is told to change
     *     their name
     */
    record Dispute(UUID premiumId, Instant raisedAt, boolean kept) {
        public Dispute {
            Objects.requireNonNull(premiumId, "premiumId");
            Objects.requireNonNull(raisedAt, "raisedAt");
        }

        /** A dispute nobody has settled. */
        public Dispute(UUID premiumId, Instant raisedAt) {
            this(premiumId, raisedAt, false);
        }
    }
}
