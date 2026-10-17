package com.uxplima.auth.paper.adapter.inbound.screen;

import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.logging.Level;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.application.lane.LaneFullException;
import com.uxplima.auth.application.message.Keys;

/**
 * A guest still connecting: there is no world yet to show a moment in, letting in ends the wait at the gate,
 * and the answer is handled on whichever thread finished the work, since no entity owns this client yet.
 */
final class ConnectingGuest implements Guest {

    private final ConfigurationGate.Waiting entry;
    private final ConfigurationGate gate;

    ConnectingGuest(ConfigurationGate.Waiting entry, ConfigurationGate gate) {
        this.entry = Objects.requireNonNull(entry, "entry");
        this.gate = Objects.requireNonNull(gate, "gate");
    }

    @Override
    public UUID id() {
        return entry.reader().id();
    }

    @Override
    public String name() {
        String name = entry.connection().getProfile().getName();
        return name == null ? "" : name;
    }

    @Override
    public String address() {
        InetSocketAddress address = entry.connection().getClientAddress();
        return address == null || address.getAddress() == null
                ? "unknown"
                : address.getAddress().getHostAddress();
    }

    @Override
    public Audience audience() {
        return entry.reader();
    }

    @Override
    public boolean may(String permission) {
        return gate.everyoneHolds(Objects.requireNonNull(permission, "permission"));
    }

    @Override
    public void fire(AuthSettings.Moment moment) {
        // Titles, sounds and effects need a world to play in; the screen itself carries the reason here.
    }

    @Override
    public void kick(Component reason) {
        entry.connection().disconnect(reason);
        gate.kicked(entry);
    }

    @Override
    public void letIn(AuthSettings.Moment moment) {
        gate.letIn(entry, moment);
    }

    @Override
    public void release() {
        gate.release(entry);
    }

    @Override
    public <T> CompletableFuture<Void> after(CompletableFuture<T> work, Consumer<T> then) {
        return work.handle((result, failure) -> {
            if (failure == null) {
                then.accept(result);
            } else {
                fail(failure);
            }
            return null;
        });
    }

    private void fail(Throwable failure) {
        Throwable cause = failure instanceof CompletionException wrapped && wrapped.getCause() != null
                ? wrapped.getCause()
                : failure;
        if (cause instanceof LaneFullException) {
            gate.showAgain(entry, gate.messages().render(entry.reader(), Keys.BUSY));
            return;
        }
        gate.logger().log(Level.WARNING, "A login on the dialog before the world failed", cause);
        kick(gate.messages().render(entry.reader(), Keys.KICK_STORAGE));
    }
}
