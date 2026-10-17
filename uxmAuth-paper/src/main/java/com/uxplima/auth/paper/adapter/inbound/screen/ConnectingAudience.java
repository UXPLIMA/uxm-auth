package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;

import org.jspecify.annotations.Nullable;

/**
 * A client in the configuration phase, as a reader: the connection's audience, with the id its session is kept
 * under and the language the client reported, so its screens can be read in that language before it has a
 * player.
 */
public final class ConnectingAudience implements ForwardingAudience.Single {

    private final UUID id;
    private final Audience audience;
    private final @Nullable Locale client;

    public ConnectingAudience(UUID id, Audience audience, @Nullable Locale client) {
        this.id = Objects.requireNonNull(id, "id");
        this.audience = Objects.requireNonNull(audience, "audience");
        this.client = client;
    }

    /** The connection the session is kept under. */
    public UUID id() {
        return id;
    }

    /** The language the client reported, if it has reported one yet. */
    public Optional<Locale> client() {
        return Optional.ofNullable(client);
    }

    @Override
    public Audience audience() {
        return audience;
    }
}
