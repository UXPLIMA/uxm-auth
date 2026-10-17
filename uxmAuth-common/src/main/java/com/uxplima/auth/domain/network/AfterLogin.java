package com.uxplima.auth.domain.network;

/** Where a player waiting on an auth server goes once logged in. */
public enum AfterLogin {
    /** A lobby, by the balance. */
    LOBBY,
    /** The server they were on when they last left, or a lobby. */
    LAST_SERVER,
    /** The server named for the address they typed, or a lobby. */
    BY_DOMAIN
}
