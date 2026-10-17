package com.uxplima.auth.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.NetworkEvents;
import com.uxplima.auth.domain.network.Bulletin;
import com.uxplima.auth.domain.network.Bulletins;
import com.uxplima.uxmlib.redis.RedisBus;

/**
 * The bulletins of a network on Redis. A member hears its own bulletin at once, without waiting on the wire, and
 * drops it when Redis brings it back; it hears every other member's on the channel, sealed with the network's
 * secret and fresh, and drops the rest with a warning.
 */
public final class RedisEvents implements NetworkEvents, AutoCloseable {

    private final RedisBus bus;
    private final String channel;
    private final Bulletins bulletins;
    private final Supplier<Instant> clock;
    private final Consumer<String> warn;
    private final UUID origin = UUID.randomUUID();
    private final List<Consumer<Bulletin>> listeners = new CopyOnWriteArrayList<>();

    public RedisEvents(
            RedisBus bus, String channel, Bulletins bulletins, Supplier<Instant> clock, Consumer<String> warn) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.bulletins = Objects.requireNonNull(bulletins, "bulletins");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /** Start hearing the channel. */
    public void start() {
        bus.subscribe(channel, this::heard);
    }

    @Override
    public void publish(Bulletin bulletin) {
        Objects.requireNonNull(bulletin, "bulletin");
        deliver(bulletin);
        String sealed = bulletins.seal(new Bulletins.Sent(bulletin, origin, clock.get()));
        bus.publish(channel, sealed.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void listen(Consumer<Bulletin> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private void heard(byte[] frame) {
        bulletins
                .open(new String(frame, StandardCharsets.UTF_8), clock.get())
                .ifPresentOrElse(
                        sent -> {
                            if (!sent.origin().equals(origin)) {
                                deliver(sent.bulletin());
                            }
                        },
                        () -> warn.accept("A message on the Redis channel " + channel + " was not sealed with this"
                                + " network's secret, or came too late, and was dropped. Every member must share"
                                + " network.secret and a clock."));
    }

    private void deliver(Bulletin bulletin) {
        for (Consumer<Bulletin> listener : listeners) {
            listener.accept(bulletin);
        }
    }

    @Override
    public void close() {
        bus.close();
    }
}
