package com.uxplima.auth.domain.network;

/** Why a backend did not take a claim. Each one is its own console line. */
public enum ClaimRefusal {
    /** It is not a signed claim at all, or it was cut short. */
    MALFORMED,
    /** It was not signed with this network's secret, or it was changed after it was signed. */
    BAD_SIGNATURE,
    /** It was written too long ago, or too far in the future. */
    STALE,
    /** It is about another player. */
    NOT_THIS_PLAYER
}
