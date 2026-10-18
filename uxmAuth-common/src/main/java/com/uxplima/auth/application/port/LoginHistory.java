package com.uxplima.auth.application.port;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.domain.state.AuthMethod;

/** Every login, with when, from where and how, kept for staff to read and for as long as the operator says. */
public interface LoginHistory {

    /** One login. */
    record Entry(Instant at, String address, AuthMethod method) {
        public Entry {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(method, "method");
        }
    }

    void record(UUID account, Entry entry);

    /** The account's latest logins, newest first, at most {@code limit}. */
    List<Entry> recent(UUID account, int limit);

    int count(UUID account);

    /** The accounts that logged in from this address, an IPv6 home by its /64, at most {@code limit}. */
    List<UUID> accountsOn(String address, int limit);

    /** Drops every login before this instant, and answers how many went. */
    int prune(Instant before);
}
