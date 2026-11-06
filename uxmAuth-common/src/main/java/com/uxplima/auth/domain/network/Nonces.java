package com.uxplima.auth.domain.network;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The nonces of the claims a member took, each kept while its claim is still fresh, so a copy of a claim is taken
 * once at most. A claim travels in a profile other plugins may read, and a copy heard again inside its fresh time
 * would otherwise let someone in a second time.
 *
 * <p>The memory is bounded. A full one refuses a new claim rather than forget one that is still fresh: the proxy
 * writes a fresh claim for every connection, so only a flood of connections inside one fresh time fills it.
 */
public final class Nonces {

    /** Far more connections than any network makes inside the few seconds a claim stays fresh. */
    public static final int MOST = 65_536;

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private final int most;

    public Nonces() {
        this(MOST);
    }

    /** @param most how many fresh nonces are kept at once; past that a new claim is refused */
    public Nonces(int most) {
        if (most < 1) {
            throw new IllegalArgumentException("a nonce memory keeps at least one, not " + most);
        }
        this.most = most;
    }

    /**
     * Whether this is the first time the nonce is seen; it is kept until it expires. False for a nonce seen
     * before, and for any nonce while the memory is full of fresh ones.
     */
    boolean first(String nonce, Instant expires, Instant now) {
        Objects.requireNonNull(nonce, "nonce");
        Objects.requireNonNull(expires, "expires");
        Objects.requireNonNull(now, "now");
        seen.values().removeIf(until -> until.isBefore(now));
        if (seen.size() >= most) {
            return false;
        }
        return seen.putIfAbsent(nonce, expires) == null;
    }
}
