package com.uxplima.auth.api.velocity.event;

import java.time.Duration;
import java.util.Objects;

/** An address is banned at the proxy after its wrong passwords. */
public record BanEvent(String address, Duration length) {
    public BanEvent {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(length, "length");
    }
}
