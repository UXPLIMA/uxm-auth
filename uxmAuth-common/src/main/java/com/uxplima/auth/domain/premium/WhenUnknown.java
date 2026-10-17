package com.uxplima.auth.domain.premium;

/** What a new name is taken for when no source could say whether it is a paid account's. */
public enum WhenUnknown {
    /** Cracked, with the account marked unverified so the next connection asks again. */
    CRACKED,
    /** Premium: the handshake is asked for. */
    PREMIUM,
    /** Refused, with a message. */
    REFUSE
}
