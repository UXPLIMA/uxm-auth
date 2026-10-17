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
    NETWORK
}
