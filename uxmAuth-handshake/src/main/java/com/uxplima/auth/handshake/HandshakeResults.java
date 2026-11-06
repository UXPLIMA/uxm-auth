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
 *
 * <p>A hello Floodgate marked a Bedrock one is kept too. Floodgate finds a linked player by the Java account's id
 * as well, which a Java client on an offline server can arrive under, so only the mark on the connection's own
 * channel tells the pre-login that this very connection is the Bedrock one.
 */
public final class HandshakeResults {

    private sealed interface Entry {}

    private record Asked(Login login, String name) implements Entry {}

    private record Let(Decision decision) implements Entry {}

    private record Bedrock() implements Entry {}

    private final Map<InetSocketAddress, Entry> entries = new ConcurrentHashMap<>();

    /** What the hello of the connection at this address came to, read once by its pre-login. */
    public Optional<HelloOutcome> take(InetSocketAddress address) {
        Entry entry = entries.remove(Objects.requireNonNull(address, "address"));
        return switch (entry) {
            case null -> Optional.empty();
            case Asked asked ->
                Optional.of(asked.login().proved() ? new HelloOutcome.Proved() : new HelloOutcome.NotProved());
            case Let let -> Optional.of(new HelloOutcome.Decided(let.decision()));
            // Floodgate marked the channel, yet its pre-login did not take the Bedrock door: no Java hello was seen.
            case Bedrock bedrock -> Optional.empty();
        };
    }

    /** Whether Floodgate marked the channel of the connection at this address a Bedrock one at its hello, read once. */
    public boolean takeBedrock(InetSocketAddress address) {
        Objects.requireNonNull(address, "address");
        Entry entry = entries.get(address);
        return entry instanceof Bedrock && entries.remove(address, entry);
    }

    void asked(Login login, String name) {
        entries.put(login.address(), new Asked(login, name));
    }

    void let(InetSocketAddress address, Decision decision) {
        entries.put(address, new Let(decision));
    }

    void bedrock(InetSocketAddress address) {
        entries.put(address, new Bedrock());
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
