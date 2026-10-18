package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** An address as it may leave the server: enough to tell two networks apart, never enough to find a home. */
final class AddressMaskTest {

    @Test
    @DisplayName("an IPv4 address keeps its first two parts")
    void ipv4KeepsTwoParts() {
        assertThat(AddressMask.of("203.0.113.7")).isEqualTo("203.0.x.x");
        assertThat(AddressMask.of("10.20.30.40")).isEqualTo("10.20.x.x");
    }

    @Test
    @DisplayName("an IPv6 address keeps its first two groups, in any spelling of it")
    void ipv6KeepsTwoGroups() {
        assertThat(AddressMask.of("2001:db8::1")).isEqualTo("2001:db8:x");
        assertThat(AddressMask.of("2001:0db8:0000:0000:0000:0000:0000:0001")).isEqualTo("2001:db8:x");
        assertThat(AddressMask.of("::ffff:203.0.113.7")).isEqualTo("203.0.x.x");
    }

    @Test
    @DisplayName("text that is no address shows nothing of itself")
    void anythingElseIsHidden() {
        assertThat(AddressMask.of("")).isEqualTo("x");
        assertThat(AddressMask.of("play.example.org")).isEqualTo("x");
    }
}
