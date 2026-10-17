package com.uxplima.auth.domain.premium;

import java.util.Objects;

/**
 * A connection at the door, as the decision needs to see it.
 *
 * @param holder what holds the name here already
 * @param asked whether the name asked for the handshake on its next connection, through a claim or an opt-in
 * @param failedBefore whether a connection from this address under this name failed the handshake lately
 * @param host the address the player typed to join
 */
public record Arrival(Holder holder, boolean asked, boolean failedBefore, String host) {
    public Arrival {
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(host, "host");
    }
}
