package com.uxplima.auth.domain.protection;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * The shared-address filters, TCPShield and the like, through which every player arrives from the filter's own
 * address. A connection is through one exactly when one of the filter's addresses made it, whatever host the
 * player typed: the host is the player's to write, and any player behind the filter may type a host the list
 * does not name, so a host never makes the filter's address one player's own. Its sessions would be resumed,
 * and its wrong passwords would ban, for everybody behind the filter.
 *
 * @param hosts the hosts the filter answers for; they no longer narrow the sources, and without sources they
 *     prove nothing
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
        return sources.contains(address);
    }

    /** Whether hosts are listed with no address to prove them, so that nothing counts as through a filter. */
    public boolean unproven() {
        return !hosts.isEmpty() && sources.written().isEmpty();
    }

    /**
     * Whether hosts are listed beside the sources. They once let a connection from a filter's address count as an
     * ordinary one when it typed another host; now every such connection is through the filter.
     */
    public boolean hostsNarrowNothing() {
        return !hosts.isEmpty() && !sources.written().isEmpty();
    }
}
