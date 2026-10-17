package com.uxplima.auth.application.port;

import java.util.function.Consumer;

import com.uxplima.auth.domain.network.Bulletin;

/**
 * What every member of a network hears at once: every proxy and every server behind them. A member that stands
 * alone, or a network without Redis, hears only itself.
 */
public interface NetworkEvents extends AutoCloseable {

    /** Tell every member, this one too. Never waits on the wire. */
    void publish(Bulletin bulletin);

    /** Hear every bulletin, this member's own too, on whatever thread brings it. */
    void listen(Consumer<Bulletin> listener);

    /** Let go of the wire, when there is one. */
    @Override
    default void close() {}
}
