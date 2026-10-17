package com.uxplima.auth.handshake;

import java.net.InetSocketAddress;

import net.minecraft.network.protocol.login.ServerboundHelloPacket;

/** One login as the handshake sees it: the server's own listener for a connection in its login phase. */
interface Login {

    /** The address the connection comes from, as the pre-login reads it. */
    InetSocketAddress address();

    /** The host the player typed to join. */
    String host();

    /**
     * Asks the client for its key, as an online server does: the listener takes the hello's name and waits for
     * the key, and the server's own code checks it, switches to AES and asks Mojang.
     */
    void askForKey(ServerboundHelloPacket hello);

    /** Whether the server's own check of the key and Mojang's answer passed. */
    boolean proved();
}
