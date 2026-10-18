package com.uxplima.auth.api.paper.event;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.uxplima.auth.api.AccountType;

/**
 * This account turned premium or cracked, by its player or by staff. Its player may be on another server, or none.
 * Fired off the server's threads.
 */
public final class KindSwitchEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID account;
    private final String name;
    private final AccountType to;

    public KindSwitchEvent(UUID account, String name, AccountType to) {
        super(true);
        this.account = Objects.requireNonNull(account, "account");
        this.name = Objects.requireNonNull(name, "name");
        this.to = Objects.requireNonNull(to, "to");
    }

    /** The account's id; a switch to premium may have moved it to the paid account's id. */
    public UUID account() {
        return account;
    }

    public String name() {
        return name;
    }

    /** What the account is now. */
    public AccountType to() {
        return to;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
