package com.uxplima.auth.paper.adapter.outbound.handshake;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.handshake.HelloDoor;
import com.uxplima.auth.paper.adapter.inbound.listener.AddressRules;
import com.uxplima.uxmlib.scheduler.Scheduler;

/**
 * The door a hello asks, off the connection's event loop: the database and the web are read on the async pool.
 *
 * <p>The address rules come first, so a banned, refused or flooding address never has the web asked about its
 * name. A connection they refuse is let through untouched, and its pre-login turns it away with their reason.
 */
public final class PremiumHelloDoor implements HelloDoor {

    private final AtTheDoor door;
    private final AddressRules rules;
    private final Scheduler scheduler;

    /** @param rules the address rules, met here once for the pre-login too */
    public PremiumHelloDoor(AtTheDoor door, AddressRules rules, Scheduler scheduler) {
        this.door = Objects.requireNonNull(door, "door");
        this.rules = Objects.requireNonNull(rules, "rules");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Decision> decide(String name, InetSocketAddress address, String host) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return CompletableFuture.supplyAsync(
                () -> rules.atHello(address, host).isPresent()
                        ? new Decision.Cracked(false, false)
                        : door.decide(name, address.getAddress().getHostAddress(), host),
                scheduler::async);
    }

    @Override
    public void failed(InetSocketAddress address, String name) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> door.failed(address.getAddress().getHostAddress(), new PlayerName(name)));
    }
}
