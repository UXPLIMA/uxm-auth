package com.uxplima.auth.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

import com.uxplima.auth.domain.state.AuthState;

/**
 * Where every connected player stands, in memory, keyed by the connection's UUID.
 *
 * <p>A connection is opened when the player arrives and closed when they leave. A hash that finishes after the
 * player left finds the connection closed, and its result is thrown away rather than written.
 *
 * <p>Every opening gets a generation of its own. A player who leaves and joins again under the same UUID
 * while a hash runs is a new connection with a new generation, and the old answer does not reach it. On an
 * offline-mode server that second connection may be somebody else using the same name.
 */
public final class Sessions {

    /**
     * A connection's state, which opening of that connection it belongs to, the visit it came as (the nonce of
     * the proxy's claim on a network, or a random id a server gave it, so a bulletin can name this session), and
     * the host the player typed, empty when nobody knows it, and whether the player ticked "remember me" on a
     * screen of this opening.
     */
    public record Held(long generation, AuthState state, String visit, String host, boolean chose) {
        public Held {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(visit, "visit");
            Objects.requireNonNull(host, "host");
        }

        private Held changed(AuthState next) {
            return new Held(generation, next, visit, host, chose);
        }
    }

    private final Map<UUID, Held> states = new ConcurrentHashMap<>();
    private final AtomicLong generations = new AtomicLong();

    /** Opens the connection as a visit of its own. */
    public void open(UUID connection, AuthState state) {
        open(connection, state, UUID.randomUUID().toString());
    }

    /** Opens the connection as the visit the proxy named. */
    public void open(UUID connection, AuthState state, String visit) {
        open(connection, state, visit, "");
    }

    /** Opens the connection as this visit, through the host the player typed. */
    public void open(UUID connection, AuthState state, String visit, String host) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(visit, "visit");
        Objects.requireNonNull(host, "host");
        states.compute(
                connection, (key, previous) -> new Held(generations.incrementAndGet(), state, visit, host, false));
    }

    public Optional<AuthState> state(UUID connection) {
        return held(connection).map(Held::state);
    }

    public Optional<Held> held(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        return Optional.ofNullable(states.get(connection));
    }

    public void close(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        states.remove(connection);
    }

    /** Closes the connection only if it is still the opening this generation names. */
    public void close(UUID connection, long generation) {
        Objects.requireNonNull(connection, "connection");
        states.computeIfPresent(connection, (key, current) -> current.generation() == generation ? null : current);
    }

    public boolean isOpen(UUID connection) {
        Objects.requireNonNull(connection, "connection");
        return states.containsKey(connection);
    }

    /** Whether this opening of the connection is still the one held. */
    public boolean isOpen(UUID connection, long generation) {
        return held(connection).filter(held -> held.generation() == generation).isPresent();
    }

    /** Changes the state of an open connection in one step and answers what it became. */
    public Optional<AuthState> update(UUID connection, UnaryOperator<AuthState> change) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(change, "change");
        Held after =
                states.computeIfPresent(connection, (key, current) -> current.changed(change.apply(current.state())));
        return Optional.ofNullable(after).map(Held::state);
    }

    /**
     * Changes the state only if the connection is still the opening this generation names, and answers what
     * it became. A later opening, or none, is left alone and answers nothing.
     */
    public Optional<AuthState> update(UUID connection, long generation, UnaryOperator<AuthState> change) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(change, "change");
        AtomicBoolean same = new AtomicBoolean();
        Held after = states.computeIfPresent(connection, (key, current) -> {
            if (current.generation() != generation) {
                return current;
            }
            same.set(true);
            return current.changed(change.apply(current.state()));
        });
        return same.get() && after != null ? Optional.of(after.state()) : Optional.empty();
    }

    /** The player ticked "remember me", or left it empty, on a screen of the opening held now. */
    public void chose(UUID connection, boolean remember) {
        Objects.requireNonNull(connection, "connection");
        states.computeIfPresent(
                connection,
                (key, current) ->
                        new Held(current.generation(), current.state(), current.visit(), current.host(), remember));
    }
}
