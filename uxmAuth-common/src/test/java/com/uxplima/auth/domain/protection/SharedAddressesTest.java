package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.premium.HostPatterns;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A connection comes through a shared-address filter only when the filter's own address made it: the host a
 * player types is theirs to write, so a host alone proves nothing.
 */
final class SharedAddressesTest {

    private static final String FILTER = "198.51.100.20";
    private static final String HOME = "203.0.113.7";

    private static SharedAddresses shield() {
        return new SharedAddresses(
                new HostPatterns(List.of("*.tcpshield.com")), new AddressRanges(List.of("198.51.100.0/24")));
    }

    @Test
    @DisplayName("the filter's address with the filter's host is through the filter")
    void theFiltersAddressAndHost() {
        assertThat(shield().through(FILTER, "mc.tcpshield.com")).isTrue();
    }

    @Test
    @DisplayName("the filter's host typed from any other address is not, so a typed host cannot escape a ban")
    void aTypedHostAloneIsNotThrough() {
        assertThat(shield().through(HOME, "mc.tcpshield.com")).isFalse();
    }

    @Test
    @DisplayName("from the filter's address, an unread host counts as the filter's and another host does not")
    void unreadAndOtherHosts() {
        assertThat(shield().through(FILTER, "")).isTrue();
        assertThat(shield().through(FILTER, "play.example.org")).isFalse();
    }

    @Test
    @DisplayName("with no filter address listed nothing is through, and the settings say they prove nothing")
    void hostsWithoutAddressesProveNothing() {
        SharedAddresses hostsOnly =
                new SharedAddresses(new HostPatterns(List.of("*.tcpshield.com")), new AddressRanges(List.of()));

        assertThat(hostsOnly.through(FILTER, "mc.tcpshield.com")).isFalse();
        assertThat(hostsOnly.unproven()).isTrue();
        assertThat(shield().unproven()).isFalse();
        assertThat(SharedAddresses.none().unproven()).isFalse();
        assertThat(SharedAddresses.none().through(FILTER, "mc.tcpshield.com")).isFalse();
    }

    @Test
    @DisplayName("with filter addresses and no host listed, every connection from them is through the filter")
    void addressesWithoutHosts() {
        SharedAddresses addressesOnly =
                new SharedAddresses(new HostPatterns(List.of()), new AddressRanges(List.of("198.51.100.0/24")));

        assertThat(addressesOnly.through(FILTER, "play.example.org")).isTrue();
        assertThat(addressesOnly.through(HOME, "play.example.org")).isFalse();
    }
}
