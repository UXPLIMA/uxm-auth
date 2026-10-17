package com.uxplima.auth.application.lane;

/**
 * The lane took no more work: its queue is full, or it has closed.
 *
 * <p>The caller tells the player to try again in a moment. Waiting instead would hold a server thread or a
 * connection open for as long as the queue ahead takes, which is what the lane exists to stop.
 */
public final class LaneFullException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LaneFullException() {
        super("the hashing lane is full or closed");
    }
}
