package com.uxplima.auth.testing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.domain.name.PlayerName;

/** Disputes in memory, one per name like the table. */
public final class InMemoryDisputes implements Disputes {

    private final Map<String, Dispute> byKey = new ConcurrentHashMap<>();

    @Override
    public void raise(PlayerName name, UUID premiumId, Instant at) {
        byKey.putIfAbsent(name.key(), new Dispute(premiumId, at));
    }

    @Override
    public Optional<Dispute> of(PlayerName name) {
        return Optional.ofNullable(byKey.get(name.key()));
    }

    @Override
    public void resolve(PlayerName name) {
        byKey.remove(name.key());
    }

    @Override
    public void keep(PlayerName name) {
        byKey.computeIfPresent(name.key(), (key, held) -> new Dispute(held.premiumId(), held.raisedAt(), true));
    }
}
