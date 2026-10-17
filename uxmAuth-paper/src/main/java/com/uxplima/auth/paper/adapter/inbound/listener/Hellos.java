package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetSocketAddress;
import java.util.Optional;

import com.uxplima.auth.handshake.HelloOutcome;

/** What the hello of each connection came to, where this server runs the handshake. */
public interface Hellos {

    /** A server that does not run the handshake: online mode, a proxy, or a login this plugin does not know. */
    Hellos NONE = new Hellos() {
        @Override
        public boolean installed() {
            return false;
        }

        @Override
        public Optional<HelloOutcome> take(InetSocketAddress address) {
            return Optional.empty();
        }
    };

    /** Whether every connection's hello passes the door before its pre-login. */
    boolean installed();

    /** What the hello of the connection at this address came to, read once. */
    Optional<HelloOutcome> take(InetSocketAddress address);
}
