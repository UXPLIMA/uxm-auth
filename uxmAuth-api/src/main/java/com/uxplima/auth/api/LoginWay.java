package com.uxplima.auth.api;

/** How a player got in. */
public enum LoginWay {
    /** Their password. */
    PASSWORD,
    /** They registered just now. */
    REGISTERED,
    /** A paid account Mojang proved. */
    PREMIUM,
    /** Xbox Live, through Floodgate. */
    BEDROCK,
    /** A session remembered from their last login on the same address. */
    SESSION,
    /** Staff let them in. */
    FORCED,
    /** The proxy logged them in, and this server took its word. */
    NETWORK
}
