package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.application.protection.Remember;
import com.uxplima.auth.domain.premium.HostPatterns;

/**
 * What protects the door.
 *
 * @param sharedAddressHosts the shared-address filters, TCPShield and the like, through which every player
 *     arrives from one address and no address rule applies
 * @param sessions for whom remembered sessions are on, and how long one lasts
 */
public record ProtectionSettings(List<String> sharedAddressHosts, Remember.Settings sessions) {
    public ProtectionSettings {
        sharedAddressHosts = List.copyOf(Objects.requireNonNull(sharedAddressHosts, "sharedAddressHosts"));
        Objects.requireNonNull(sessions, "sessions");
    }

    /** The shared-address filters, to match the host a player typed against. */
    public HostPatterns sharedHosts() {
        return new HostPatterns(sharedAddressHosts);
    }
}
