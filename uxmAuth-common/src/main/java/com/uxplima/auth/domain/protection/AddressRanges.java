package com.uxplima.auth.domain.protection;

import java.net.InetAddress;
import java.util.List;
import java.util.Objects;

/**
 * The addresses an operator lists: one address, or a range written as an address and its prefix length, as
 * {@code 10.0.0.0/8}. An IPv4 address written inside IPv6 matches as the IPv4 address it is.
 */
public final class AddressRanges {

    /** The first address of a range and how many of its leading bits every address in it shares. */
    private static final class Range {
        private final byte[] start;
        private final int prefix;

        Range(byte[] start, int prefix) {
            this.start = start.clone();
            this.prefix = prefix;
        }

        byte[] start() {
            return start;
        }

        int prefix() {
            return prefix;
        }
    }

    private final List<String> written;
    private final List<Range> ranges;

    public AddressRanges(List<String> written) {
        this.written = List.copyOf(Objects.requireNonNull(written, "written"));
        this.ranges = this.written.stream().map(AddressRanges::range).toList();
    }

    /** What the operator wrote, in order. */
    public List<String> written() {
        return written;
    }

    /** Whether the address falls inside one of the ranges; text that is no address is in none. */
    public boolean contains(String address) {
        Objects.requireNonNull(address, "address");
        if (ranges.isEmpty() || !AddressKey.isAddress(address)) {
            return false;
        }
        byte[] bytes = InetAddress.ofLiteral(address).getAddress();
        return ranges.stream().anyMatch(range -> within(bytes, range));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AddressRanges ranges && ranges.written.equals(written);
    }

    @Override
    public int hashCode() {
        return written.hashCode();
    }

    @Override
    public String toString() {
        return "AddressRanges" + written;
    }

    private static boolean within(byte[] address, Range range) {
        if (address.length != range.start().length) {
            return false;
        }
        int whole = range.prefix() / 8;
        for (int i = 0; i < whole; i++) {
            if (address[i] != range.start()[i]) {
                return false;
            }
        }
        int rest = range.prefix() % 8;
        if (rest == 0) {
            return true;
        }
        int mask = (0xff << (8 - rest)) & 0xff;
        return (address[whole] & mask) == (range.start()[whole] & mask);
    }

    private static Range range(String line) {
        String text = line.strip();
        int slash = text.indexOf('/');
        String address = slash < 0 ? text : text.substring(0, slash);
        if (!AddressKey.isAddress(address)) {
            throw new IllegalArgumentException("Not an address or a range: " + line);
        }
        byte[] start = InetAddress.ofLiteral(address).getAddress();
        int bits = start.length * 8;
        int prefix;
        try {
            prefix = slash < 0 ? bits : Integer.parseInt(text.substring(slash + 1));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("Not a prefix length in " + line, notANumber);
        }
        if (prefix < 0 || prefix > bits) {
            throw new IllegalArgumentException("A prefix of " + prefix + " does not fit " + line);
        }
        return new Range(start, prefix);
    }
}
