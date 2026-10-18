package com.uxplima.auth.domain.protection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The addresses an operator lists, one by one or as a range with its prefix length. */
final class AddressRangesTest {

    @Test
    @DisplayName("an address matches itself, and a range every address under its prefix")
    void addressesAndRanges() {
        AddressRanges ranges = new AddressRanges(List.of("203.0.113.7", "10.0.0.0/8", "2001:db8::/32"));

        assertThat(ranges.contains("203.0.113.7")).isTrue();
        assertThat(ranges.contains("203.0.113.8")).isFalse();
        assertThat(ranges.contains("10.200.3.4")).isTrue();
        assertThat(ranges.contains("11.0.0.1")).isFalse();
        assertThat(ranges.contains("2001:db8:ffff::1")).isTrue();
        assertThat(ranges.contains("2001:db9::1")).isFalse();
        assertThat(ranges.contains("::ffff:10.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("a prefix that does not fall on a byte still cuts where it says, and nothing listed matches nothing")
    void oddPrefixes() {
        AddressRanges ranges = new AddressRanges(List.of("192.168.0.0/23"));

        assertThat(ranges.contains("192.168.1.255")).isTrue();
        assertThat(ranges.contains("192.168.2.0")).isFalse();
        assertThat(new AddressRanges(List.of()).contains("192.168.1.1")).isFalse();
        assertThat(ranges.contains("unknown")).isFalse();
    }

    @Test
    @DisplayName("a line that is no address, or a prefix longer than the address, is refused when the file is read")
    void badLinesAreRefused() {
        assertThatThrownBy(() -> new AddressRanges(List.of("example.org")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("example.org");
        assertThatThrownBy(() -> new AddressRanges(List.of("10.0.0.0/33")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AddressRanges(List.of("10.0.0.0/x"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an IPv6 range never holds an IPv4 address, nor an IPv4 range an IPv6 one")
    void familiesStayApart() {
        assertThat(new AddressRanges(List.of("::/0")).contains("10.1.1.1")).isFalse();
        assertThat(new AddressRanges(List.of("0.0.0.0/0")).contains("2001:db8::1"))
                .isFalse();
        assertThat(new AddressRanges(List.of("0.0.0.0/0")).contains("10.1.1.1")).isTrue();
    }

    @Test
    @DisplayName("two lists written alike are equal, so a reload can tell a change from none")
    void equalWhenWrittenAlike() {
        assertThat(new AddressRanges(List.of("10.0.0.0/8"))).isEqualTo(new AddressRanges(List.of("10.0.0.0/8")));
        assertThat(new AddressRanges(List.of("10.0.0.0/8")))
                .hasSameHashCodeAs(new AddressRanges(List.of("10.0.0.0/8")));
        assertThat(new AddressRanges(List.of("10.0.0.0/8"))).isNotEqualTo(new AddressRanges(List.of("10.0.0.0/9")));
    }
}
