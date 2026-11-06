package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** The accounts nobody logged in to since a time, for the purge. */
@FunctionalInterface
public interface InactiveAccounts {

    /**
     * The accounts whose last login, or registration when they never logged in, came before {@code before}, at
     * most {@code limit} of them, in the order of their ids and after {@code after}, so a purge that leaves some of
     * them alone still reaches the rest.
     *
     * @param keepPremium leaves premium accounts out
     * @param after the last id of the previous batch, or null for the first
     */
    List<UUID> inactiveSince(Instant before, boolean keepPremium, @Nullable UUID after, int limit);
}
