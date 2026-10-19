package com.uxplima.auth.domain.premium;

/** What holds a name here already, as the door finds it. */
public enum Holder {
    /** No account holds the name. */
    NOBODY,
    /** A cracked account the web was asked about, or did not need to be. */
    CRACKED,
    /** A cracked account taken while every source was down, which the next connection asks about again. */
    CRACKED_UNVERIFIED,
    /** An account that passed the handshake, with no password. */
    PREMIUM,
    /** An account that passed the handshake and keeps a password, which a failed handshake falls back to. */
    PREMIUM_WITH_PASSWORD,
    /** A Bedrock account Floodgate vouched for. */
    BEDROCK
}
