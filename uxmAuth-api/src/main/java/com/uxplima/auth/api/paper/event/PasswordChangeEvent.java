package com.uxplima.auth.api.paper.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** The password of this account changed, by its player or by staff. Its player may be on another server, or none. Fired off the server's threads. */
public final class PasswordChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID account;
    private final String name;

    public PasswordChangeEvent(UUID account, String name) {
        super(true);
        this.account = Objects.requireNonNull(account, "account");
        this.name = Objects.requireNonNull(name, "name");
    }

    /** The account's id, which is its player's id. */
    public UUID account() {
        return account;
    }

    public String name() {
        return name;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
