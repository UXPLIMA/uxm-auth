package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The sessions players are remembered by, and each account's choice of them, shared by a whole network. */
public interface RememberedSessions {

    /**
     * Remember the account from this address, through this host, until then, in place of what was remembered from
     * the address before.
     */
    void remember(UUID account, String address, String host, Instant until);

    /** Whether a session of the account from this address, through this same host, still holds now. */
    boolean holds(UUID account, String address, String host, Instant now);

    /** End every session of the account. */
    void forget(UUID account);

    /** What the account chose, when it chose. */
    Optional<Boolean> choice(UUID account);

    /** Keep the account's choice. */
    void choose(UUID account, boolean remember);
}
