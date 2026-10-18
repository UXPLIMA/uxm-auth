package com.uxplima.auth.domain.protection;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * The shared-address filters, TCPShield and the like, through which every player arrives from the filter's own
 * address. A connection is through one only when one of the filter's addresses made it, and, while hosts are
 * listed, the player typed one of them or a host the door could not read. The host is the player's to write, so
 * a host alone proves nothing: from any other address it is an ordinary connection.
 *
 * @param hosts the hosts the filter answers for, every host when none is listed
 * @param sources the addresses the filter connects from
 */
public record SharedAddresses(HostPatterns hosts, AddressRanges sources) {
    public SharedAddresses {
        Objects.requireNonNull(hosts, "hosts");
        Objects.requireNonNull(sources, "sources");
    }

    /** No filter at all. */
    public static SharedAddresses none() {
        return new SharedAddresses(new HostPatterns(List.of()), new AddressRanges(List.of()));
    }

    /** Whether a connection from this address, which typed this host, came through a filter. */
    public boolean through(String address, String host) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        return sources.contains(address) && (hosts.isEmpty() || host.isBlank() || hosts.matches(host));
    }

    /** Whether hosts are listed with no address to prove them, so that nothing counts as through a filter. */
    public boolean unproven() {
        return !hosts.isEmpty() && sources.written().isEmpty();
    }
}
