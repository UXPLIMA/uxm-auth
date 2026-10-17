package com.uxplima.auth.domain.premium;

/** Who owns a name, as the operator chose in config.conf. */
public enum NameRule {
    /** A new name Mojang knows is asked for the handshake once; whoever takes it first keeps it until its owner proves it. */
    AUTOMATIC,
    /** Everybody joins with a password, and a player who asks is given the handshake from then on. */
    OPT_IN,
    /** Every name Mojang knows must pass the handshake. */
    STRICT,
    /** The address a player joins through says which: one for paid accounts, the rest cracked. */
    DOMAIN
}
