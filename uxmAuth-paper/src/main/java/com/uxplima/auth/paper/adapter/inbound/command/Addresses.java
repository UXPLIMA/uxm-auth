package com.uxplima.auth.paper.adapter.inbound.command;

import java.net.InetSocketAddress;

import org.bukkit.entity.Player;

/** The address a player connected from, as the account records it. */
public final class Addresses {

    /** What is recorded when the server cannot say, which a proxy without forwarding can cause. */
    static final String UNKNOWN = "unknown";

    private Addresses() {}

    public static String of(Player player) {
        InetSocketAddress address = player.getAddress();
        return address == null || address.getAddress() == null
                ? UNKNOWN
                : address.getAddress().getHostAddress();
    }
}
