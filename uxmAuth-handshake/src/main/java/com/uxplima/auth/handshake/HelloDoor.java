package com.uxplima.auth.handshake;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.domain.premium.Decision;

/** What the hello asks the rest of the plugin, which knows the accounts and the operator's rule. */
public interface HelloDoor {

    /**
     * Whether a connection under this name, from this address, through this host, is asked for the handshake.
     * The answer may take the database and the web, so it never arrives on the thread that asked.
     */
    CompletableFuture<Decision> decide(String name, InetSocketAddress address, String host);

    /** A connection asked for the key went away without proving it. */
    void failed(InetSocketAddress address, String name);
}
