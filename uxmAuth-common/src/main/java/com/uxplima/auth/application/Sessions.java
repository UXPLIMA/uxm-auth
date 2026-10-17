package com.uxplima.auth.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.state.AuthState;

/**
 * Where every connected player stands, in memory, keyed by the connection's UUID.
 *
 * <p>A connection is opened when the player arrives and closed when they leave. A hash that finishes after the
 * player left finds the connection closed, and its result is thrown away rather than written: that is the
 * whole reason this map exists apart from the account store.
 */
public final class Sessions {

    private final Map<UUID, AuthState> states = new ConcurrentHashMap<>();

    public void open(UUID connection, AuthState state) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(state, "state");
        states.compute(connection, (key, previous) -> state);
    }

    public Optional<AuthState> state(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        return Optional.ofNullable(states.get(connection));
    }

    public void close(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        states.remove(connection);
    }

    public boolean isOpen(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        return states.containsKey(connection);
    }

    /**
     * Changes the state of an open connection in one step, and answers what it became. A closed connection
     * stays closed and answers nothing.
     */
    public Optional<AuthState> update(UUID connection, UnaryOperator<AuthState> change) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(change, "change");
        return Optional.ofNullable(states.computeIfPresent(connection, (key, current) -> change.apply(current)));
    }
}
