package com.uxplima.auth.paper.adapter.inbound.listener;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.auth.application.protection.Admission;

/**
 * The door's address rules, met once by every connection: at its hello where this server runs the handshake, and
 * at its pre-login otherwise.
 *
 * <p>The hello comes first and may ask the web whether its name is a paid account's, so a banned address, a
 * refused country or an address that connects too often is ruled on there, before it can spend Mojang's cap. Its
 * answer is kept by the connection's address and port until the pre-login reads it, so the connection is counted
 * once and turned away there with the reason the rule gave. One that never reaches its pre-login is let go after
 * a minute.
 */
public final class AddressRules {

    /** What the rules said at a hello, kept for its pre-login. */
    private record Kept(Optional<Admission.Refused> refused, Instant at) {}

    /** How long an answer waits for its pre-login. */
    private static final Duration KEEP = Duration.ofMinutes(1);

    /** Past this many kept answers, every new one first lets the old ones go. */
    private static final int SWEEP_PAST = 256;

    private final Admission admission;
    private final Clock clock;
    private final Map<InetSocketAddress, Kept> kept = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> nextSweep = new AtomicReference<>(Instant.MIN);

    public AddressRules(Admission admission, Clock clock) {
        this.admission = Objects.requireNonNull(admission, "admission");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** At the hello of the connection at this address, which typed this host: why it is turned away, if it is. */
    public Optional<Admission.Refused> atHello(InetSocketAddress connection, String host) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(host, "host");
        Optional<Admission.Refused> refused =
                admission.decide(new Admission.Arrival(connection.getAddress().getHostAddress(), host));
        Instant now = clock.instant();
        kept.put(connection, new Kept(refused, now));
        // At most once a second: a flood of connections would otherwise scan every entry at each hello.
        Instant due = nextSweep.get();
        if (kept.size() > SWEEP_PAST && !now.isBefore(due) && nextSweep.compareAndSet(due, now.plusSeconds(1))) {
            kept.values().removeIf(old -> old.at().isBefore(now.minus(KEEP)));
        }
        return refused;
    }

    /**
     * At the pre-login: what the rules said at this connection's hello, or what they say now of one whose hello
     * they never met.
     */
    public Optional<Admission.Refused> atPreLogin(InetSocketAddress connection, Admission.Arrival arrival) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(arrival, "arrival");
        Kept atHello = kept.remove(connection);
        if (atHello != null && !atHello.at().isBefore(clock.instant().minus(KEEP))) {
            return atHello.refused();
        }
        return admission.decide(arrival);
    }
}
