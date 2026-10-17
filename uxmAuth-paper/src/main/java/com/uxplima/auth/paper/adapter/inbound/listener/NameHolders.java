package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.entity.Player;

/**
 * Who already holds a name, and from where: a player standing in the world, or a client still waiting at the
 * dialog before the world. Either one makes the name online.
 */
public final class NameHolders implements Function<UUID, Optional<InetAddress>> {

    private final Server server;
    private final Function<UUID, Optional<InetAddress>> waiting;

    public NameHolders(Server server, Function<UUID, Optional<InetAddress>> waiting) {
        this.server = Objects.requireNonNull(server, "server");
        this.waiting = Objects.requireNonNull(waiting, "waiting");
    }

    /** The address of the connection that holds this id, or empty when none does. */
    @Override
    public Optional<InetAddress> apply(UUID id) {
        Objects.requireNonNull(id, "id");
        Player online = server.getPlayer(id);
        if (online == null) {
            return waiting.apply(id);
        }
        InetSocketAddress address = online.getAddress();
        // A player whose address the server no longer knows still holds the name; no address matches theirs.
        return Optional.of(address == null ? InetAddress.getLoopbackAddress() : address.getAddress());
    }
}
