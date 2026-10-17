package com.uxplima.auth.application.port;

import java.util.Optional;
import java.util.UUID;

/**
 * The server each account was on when its player last left the network, for the proxy to send them back to.
 * Every method blocks on the database.
 */
public interface LastServers {

    /** The server the player of this account was on when they last left. */
    Optional<String> of(UUID accountId);

    /** The player of this account is on this server now. */
    void remember(UUID accountId, String server);
}
