package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The accounts nobody logged in to since a time, for the purge. */
@FunctionalInterface
public interface InactiveAccounts {

    /**
     * The accounts whose last login, or registration when they never logged in, came before {@code before}, at
     * most {@code limit} of them.
     *
     * @param keepPremium leaves premium accounts out
     */
    List<UUID> inactiveSince(Instant before, boolean keepPremium, int limit);
}
