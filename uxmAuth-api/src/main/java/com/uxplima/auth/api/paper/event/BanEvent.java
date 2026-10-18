package com.uxplima.auth.api.paper.event;

import java.time.Duration;
import java.util.Objects;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** An address is banned after its wrong passwords. Fired off the server's threads. */
public final class BanEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String address;
    private final Duration length;

    public BanEvent(String address, Duration length) {
        super(true);
        this.address = Objects.requireNonNull(address, "address");
        this.length = Objects.requireNonNull(length, "length");
    }

    public String address() {
        return address;
    }

    public Duration length() {
        return length;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
