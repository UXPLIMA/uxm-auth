package com.uxplima.auth.domain.premium;

/** Why a connection is refused at the door, each a plain sentence the player reads. */
public enum Refusal {
    /** No source could say whether the name is a paid account's, and the operator refuses such names. */
    LOOKUP_FAILED,
    /** The name is a paid account's, this server lets only its owner use it, and the handshake failed. */
    PAID_NAME,
    /** The handshake failed on the address for paid accounts; the other address takes a password. */
    PREMIUM_ADDRESS,
    /** A cracked account and a paid owner both want the name, and staff have not settled it yet. */
    NAME_DISPUTED,
    /** The paid owner proved a name a cracked account holds, and here the cracked account keeps it. */
    NAME_KEPT
}
