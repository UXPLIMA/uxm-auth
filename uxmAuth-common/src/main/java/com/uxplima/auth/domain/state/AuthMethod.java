package com.uxplima.auth.domain.state;

/** How a connection came to be logged in. The effects and the history read it. */
public enum AuthMethod {
    /** The player typed the password. */
    PASSWORD,
    /** The player has just made the account. */
    REGISTERED,
    /** The client proved it owns the premium name. */
    PREMIUM,
    /** Floodgate vouched for a Bedrock player. */
    BEDROCK,
    /** A session from an earlier login was still valid. */
    SESSION,
    /** A member of staff or the API logged the player in. */
    FORCED,
    /** The player logged in on another server of the network, and the proxy vouched for it here. */
    NETWORK,
    /**
     * As {@link #NETWORK}, for a login that a remembered session made on the network: nobody typed the password.
     * Everything that reads {@link #NETWORK} reads this the same, but what a session must not do, it must not do
     * here either.
     */
    NETWORK_SESSION;

    /** Whether the proxy vouched for a login made on another server. */
    public boolean fromTheNetwork() {
        return this == NETWORK || this == NETWORK_SESSION;
    }

    /** Whether a remembered session made the login, here or on another server, and no password was typed. */
    public boolean remembered() {
        return this == SESSION || this == NETWORK_SESSION;
    }
}
