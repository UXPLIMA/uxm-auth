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

    /** Who proved the name, and when. */
    record Dispute(UUID premiumId, Instant raisedAt) {
        public Dispute {
            Objects.requireNonNull(premiumId, "premiumId");
            Objects.requireNonNull(raisedAt, "raisedAt");
        }
    }
}
