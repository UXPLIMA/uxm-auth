package com.uxplima.auth.paper.adapter.inbound.hold;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.adapter.outbound.AuthSettings;

/**
 * The way in of a client that got in before it had a player: the dialog before the world records it, and the
 * door runs it once the player stands in the world to see it.
 */
public final class Welcomes {

    private final Map<UUID, AuthSettings.Moment> waiting = new ConcurrentHashMap<>();

    /** Keeps the moment a connection got in with, until its player joins. */
    public void keep(UUID connection, AuthSettings.Moment moment) {
        waiting.put(Objects.requireNonNull(connection, "connection"), Objects.requireNonNull(moment, "moment"));
    }

    /** The moment kept for this connection, handed over once. */
    public Optional<AuthSettings.Moment> take(UUID connection) {
        return Optional.ofNullable(waiting.remove(Objects.requireNonNull(connection, "connection")));
    }

    /** Drops a moment whose connection closed before its player joined. */
    public void forget(UUID connection) {
        waiting.remove(Objects.requireNonNull(connection, "connection"));
    }
}
