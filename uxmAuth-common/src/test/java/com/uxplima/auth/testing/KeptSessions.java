package com.uxplima.auth.testing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.application.port.RememberedSessions;

/** Remembered sessions in memory, keyed by account and address, for a test that needs no database. */
public final class KeptSessions implements RememberedSessions {

    public final Map<String, Instant> until = new ConcurrentHashMap<>();
    public final Map<UUID, Boolean> choices = new ConcurrentHashMap<>();

    @Override
    public void remember(UUID account, String address, Instant expires) {
        until.put(account + "@" + address, expires);
    }

    @Override
    public boolean holds(UUID account, String address, Instant now) {
        Instant expires = until.get(account + "@" + address);
        return expires != null && now.isBefore(expires);
    }

    @Override
    public void forget(UUID account) {
        until.keySet().removeIf(key -> key.startsWith(account + "@"));
    }

    @Override
    public Optional<Boolean> choice(UUID account) {
        return Optional.ofNullable(choices.get(account));
    }

    @Override
    public void choose(UUID account, boolean remember) {
        choices.put(account, remember);
    }
}
