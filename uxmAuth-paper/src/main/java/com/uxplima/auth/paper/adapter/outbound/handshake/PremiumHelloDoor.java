package com.uxplima.auth.paper.adapter.outbound.handshake;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Decision;
import com.uxplima.auth.handshake.HelloDoor;
import com.uxplima.uxmlib.scheduler.Scheduler;

/** The door a hello asks, off the connection's event loop: the database and the web are read on the async pool. */
public final class PremiumHelloDoor implements HelloDoor {

    private final AtTheDoor door;
    private final Scheduler scheduler;

    public PremiumHelloDoor(AtTheDoor door, Scheduler scheduler) {
        this.door = Objects.requireNonNull(door, "door");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public CompletableFuture<Decision> decide(String name, InetSocketAddress address, String host) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return CompletableFuture.supplyAsync(
                () -> door.decide(name, address.getAddress().getHostAddress(), host), scheduler::async);
    }

    @Override
    public void failed(InetSocketAddress address, String name) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(name, "name");
        scheduler.async(() -> door.failed(address.getAddress().getHostAddress(), new PlayerName(name)));
    }
}
