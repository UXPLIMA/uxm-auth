package com.uxplima.auth.domain.state;

/** How many wrong passwords a connection has typed, against how many it may. */
public record Attempts(int used, int max) {

    public Attempts {
        if (max < 1) {
            throw new IllegalArgumentException("a player needs at least one attempt, got " + max);
        }
        if (used < 0 || used > max) {
            throw new IllegalArgumentException("used must be between 0 and " + max + ", got " + used);
        }
    }

    /** No attempt used yet. */
    public static Attempts fresh(int max) {
        return new Attempts(0, max);
    }

    /** One more wrong password. Past the limit the count stays at the limit. */
    public Attempts failed() {
        return new Attempts(Math.min(used + 1, max), max);
    }

    public int left() {
        return max - used;
    }

    public boolean exhausted() {
        return used >= max;
    }
}
