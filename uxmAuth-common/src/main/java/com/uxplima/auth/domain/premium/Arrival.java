package com.uxplima.auth.domain.premium;

import java.util.Objects;

/**
 * A connection at the door, as the decision needs to see it.
 *
 * @param typed the name exactly as the client sent it
 * @param holder what holds the name here already
 * @param otherCase whether the account that holds the name registered it in another spelling than {@code typed}
 * @param asked whether the name asked for the handshake on its next connection, through a claim or an opt-in
 * @param failedBefore whether a connection from this address under this name failed the handshake lately
 * @param shared whether the connection came through a shared-address filter, whose address is every player's
 *     there, so what it did lately may have been another player's doing
 * @param host the address the player typed to join
 */
public record Arrival(
        String typed,
        Holder holder,
        boolean otherCase,
        boolean asked,
        boolean failedBefore,
        boolean shared,
        String host) {
    public Arrival {
        Objects.requireNonNull(typed, "typed");
        Objects.requireNonNull(holder, "holder");
        Objects.requireNonNull(host, "host");
    }
}
