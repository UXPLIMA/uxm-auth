package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.uxmlib.storage.StorageException;

/**
 * Clears the rows that can no longer change anything: addresses quiet for as long as the bans forgive, and
 * sessions that ran out. Every connection from a new address can write a row, so without this a botnet that
 * tries each address once grows the tables for good. The platform runs it off the server thread, once a day; on a
 * network every member may, and a row one cleared is simply not there for the next.
 */
public final class TableUpkeep implements Runnable {

    private final SqlAddressTallies addresses;
    private final SqlRememberedSessions sessions;
    private final Supplier<Duration> forgiveAfter;
    private final Clock clock;
    private final Consumer<String> console;

    /**
     * @param forgiveAfter how long an address stays quiet before the bans count it from the first step again
     * @param console where a failure is said; the run tries again the next day
     */
    public TableUpkeep(
            SqlAddressTallies addresses,
            SqlRememberedSessions sessions,
            Supplier<Duration> forgiveAfter,
            Clock clock,
            Consumer<String> console) {
        this.addresses = Objects.requireNonNull(addresses, "addresses");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.forgiveAfter = Objects.requireNonNull(forgiveAfter, "forgiveAfter");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.console = Objects.requireNonNull(console, "console");
    }

    @Override
    public void run() {
        Instant now = clock.instant();
        try {
            addresses.forgetQuietSince(now.minus(forgiveAfter.get()));
            sessions.dropExpired(now);
        } catch (StorageException failed) {
            console.accept("Could not clear forgiven addresses and ended sessions; the next day tries again: "
                    + failed.getMessage());
        }
    }
}
