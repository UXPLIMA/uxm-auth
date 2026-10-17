package com.uxplima.auth.handshake;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.auth.domain.premium.Decision;

/**
 * What each hello came to, kept by the connection's address until its pre-login reads it.
 *
 * <p>The address is the one the server reads for the pre-login too, the client's own behind a proxy protocol,
 * with its port, so it names one live connection. An entry nobody read is let go when its connection closes.
 */
public final class HandshakeResults {

    private sealed interface Entry {}

    private record Asked(Login login, String name) implements Entry {}

    private record Let(Decision decision) implements Entry {}

    private final Map<InetSocketAddress, Entry> entries = new ConcurrentHashMap<>();

    /** What the hello of the connection at this address came to, read once by its pre-login. */
    public Optional<HelloOutcome> take(InetSocketAddress address) {
        Entry entry = entries.remove(Objects.requireNonNull(address, "address"));
        return switch (entry) {
            case null -> Optional.empty();
            case Asked asked ->
                Optional.of(asked.login().proved() ? new HelloOutcome.Proved() : new HelloOutcome.NotProved());
            case Let let -> Optional.of(new HelloOutcome.Decided(let.decision()));
        };
    }

    void asked(Login login, String name) {
        entries.put(login.address(), new Asked(login, name));
    }

    void let(InetSocketAddress address, Decision decision) {
        entries.put(address, new Let(decision));
    }

    /** The connection closed. The name it was asked the key under, when it never reached its pre-login. */
    Optional<String> closed(InetSocketAddress address) {
        Entry entry = entries.remove(address);
        return entry instanceof Asked asked ? Optional.of(asked.name()) : Optional.empty();
    }

    /** How many hellos wait for their pre-login, for a test that proves they are let go. */
    int size() {
        return entries.size();
    }
}
