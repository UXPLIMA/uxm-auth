package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Objects;

import com.uxplima.auth.adapter.outbound.geo.GeoSettings;
import com.uxplima.auth.application.protection.AccountCap;
import com.uxplima.auth.application.protection.Captchas;
import com.uxplima.auth.application.protection.ConnectionWindow;
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
 * @param wideBans how the wrong passwords of a whole IPv6 /48 ban it: the steps of the bans, with a limit of its own
 * @param connections how often one address may connect
 * @param accounts how many password accounts one address may make
 * @param countries which countries come in, and how the country file is fetched
 * @param captcha when a captcha stands before a registration or a login, and how it is drawn
 */
public record ProtectionSettings(
        List<String> sharedAddressHosts,
        List<String> sharedAddressSources,
        Remember.Settings sessions,
        BanSteps bans,
        BanSteps wideBans,
        ConnectionWindow.Settings connections,
        AccountCap.Settings accounts,
        GeoSettings countries,
        Captchas.Settings captcha) {
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
        Objects.requireNonNull(wideBans, "wideBans");
        Objects.requireNonNull(connections, "connections");
        Objects.requireNonNull(accounts, "accounts");
        Objects.requireNonNull(countries, "countries");
        Objects.requireNonNull(captcha, "captcha");
    }

    /** What the console should say about these settings at every start and reload. */
    public List<String> problems() {
        SharedAddresses shared = sharedAddresses();
        if (shared.unproven()) {
            return List.of("protection.shared-address-hosts lists hosts, but protection.shared-address-sources lists"
                    + " no address, so no connection counts as through a filter: a host alone is the player's to"
                    + " type. List the addresses the filter connects from.");
        }
        if (shared.hostsNarrowNothing()) {
            return List.of("protection.shared-address-hosts no longer narrows protection.shared-address-sources:"
                    + " every connection from those addresses counts as through the filter, whatever host it"
                    + " typed, so none of them is remembered or banned by address. Empty the hosts list to"
                    + " silence this.");
        }
        return List.of();
    }

    /** The shared-address filters, to tell a connection through one from an ordinary one. */
    public SharedAddresses sharedAddresses() {
        return new SharedAddresses(new HostPatterns(sharedAddressHosts), new AddressRanges(sharedAddressSources));
    }
}
