package com.uxplima.auth.domain.account;

/** How an account proves who it belongs to. */
public enum AccountKind {
    /** A password the player chose. */
    CRACKED,
    /** The Mojang handshake. */
    PREMIUM,
    /** Xbox Live, through Floodgate. */
    BEDROCK
}
