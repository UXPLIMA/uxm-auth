package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.premium.HostPatterns;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.BanSteps;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * What protects the door.
 *
 * @param sharedAddressHosts the hosts of the shared-address filters, TCPShield and the like, through which every
 *     player arrives from the filter's address and no address rule applies
 * @param sharedAddressSources the addresses those filters connect from, which prove a connection came through one
 * @param sessions for whom remembered sessions are on, and how long one lasts
 * @param bans how wrong passwords ban the address they came from
 */
public record ProtectionSettings(
        List<String> sharedAddressHosts, List<String> sharedAddressSources, Remember.Settings sessions, BanSteps bans) {
    public ProtectionSettings {
        sharedAddressHosts = List.copyOf(Objects.requireNonNull(sharedAddressHosts, "sharedAddressHosts"));
        sharedAddressSources = List.copyOf(Objects.requireNonNull(sharedAddressSources, "sharedAddressSources"));
        try {
            new AddressRanges(sharedAddressSources);
        } catch (IllegalArgumentException wrong) {
            throw new IllegalArgumentException("protection.shared-address-sources: " + wrong.getMessage(), wrong);
        }
        Objects.requireNonNull(sessions, "sessions");
        Objects.requireNonNull(bans, "bans");
    }

    /** What the console should say about these settings at every start and reload. */
    public List<String> problems() {
        if (!sharedAddresses().unproven()) {
            return List.of();
        }
        return List.of("protection.shared-address-hosts lists hosts, but protection.shared-address-sources lists no"
                + " address, so no connection counts as through a filter: a host alone is the player's to type."
                + " List the addresses the filter connects from.");
    }

    /** The shared-address filters, to tell a connection through one from an ordinary one. */
    public SharedAddresses sharedAddresses() {
        return new SharedAddresses(new HostPatterns(sharedAddressHosts), new AddressRanges(sharedAddressSources));
    }
}
