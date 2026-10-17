package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * What protects the door.
 *
 * @param sharedAddressHosts the shared-address filters, TCPShield and the like, through which every player
 *     arrives from one address and no address rule applies
 */
public record ProtectionSettings(List<String> sharedAddressHosts) {
    public ProtectionSettings {
        sharedAddressHosts = List.copyOf(Objects.requireNonNull(sharedAddressHosts, "sharedAddressHosts"));
    }

    /** The shared-address filters, to match the host a player typed against. */
    public HostPatterns sharedHosts() {
        return new HostPatterns(sharedAddressHosts);
    }
}
