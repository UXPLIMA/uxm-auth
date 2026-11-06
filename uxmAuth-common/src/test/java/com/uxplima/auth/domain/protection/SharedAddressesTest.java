package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.uxplima.auth.domain.premium.HostPatterns;
import org.junit.jupiter.api.Test;

class SharedAddressesTest {

    private static final String FILTER = "198.51.100.7";

    @Test
    void everyConnectionFromTheFilterIsThroughItWhateverHostItTyped() {
        SharedAddresses shared = new SharedAddresses(
                new HostPatterns(List.of("play.example.com")), new AddressRanges(List.of("198.51.100.0/24")));

        assertThat(shared.through(FILTER, "play.example.com")).isTrue();
        assertThat(shared.through(FILTER, "somewhere.else")).isTrue();
        assertThat(shared.through(FILTER, "")).isTrue();
        assertThat(shared.through("203.0.113.9", "play.example.com")).isFalse();
        assertThat(shared.hostsNarrowNothing()).isTrue();
    }

    @Test
    void hostsWithNoSourcesProveNothing() {
        SharedAddresses shared =
                new SharedAddresses(new HostPatterns(List.of("play.example.com")), new AddressRanges(List.of()));

        assertThat(shared.through(FILTER, "play.example.com")).isFalse();
        assertThat(shared.unproven()).isTrue();
    }
}
