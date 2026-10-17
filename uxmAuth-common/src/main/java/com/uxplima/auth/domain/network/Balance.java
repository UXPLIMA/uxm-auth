package com.uxplima.auth.domain.network;

/** How a server is picked from a list: the one with the fewest players, one at random, or the first that is up. */
public enum Balance {
    LEAST_PLAYERS,
    RANDOM,
    FIRST
}
