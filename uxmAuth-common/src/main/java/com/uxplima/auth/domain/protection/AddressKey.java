package com.uxplima.auth.domain.protection;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

import org.jspecify.annotations.Nullable;

/**
 * The key an address is counted under. An IPv4 address is its own; an IPv6 address counts by its /64, because a
 * home is handed a whole /64 and could otherwise fail from a new address every time. An IPv6 address also has a
 * wide key, its /48: a provider hands one customer a /48 or a /56, so a guesser can hop between thousands of /64s
 * without leaving it.
 */
public final class AddressKey {

    private static final int PREFIX_HEXTETS = 4;
    private static final int WIDE_HEXTETS = 3;

    private AddressKey() {}

    /** Whether the text is an address literal, as the server writes one it could read. */
    public static boolean isAddress(String text) {
        Objects.requireNonNull(text, "text");
        try {
            InetAddress.ofLiteral(text);
            return true;
        } catch (IllegalArgumentException notALiteral) {
            return false;
        }
    }

    /** The key of this address; text that is no address literal is its own key. */
    public static String of(String address) {
        Objects.requireNonNull(address, "address");
        if (address.indexOf(':') < 0) {
            return address;
        }
        InetAddress parsed = literal(address);
        if (parsed == null) {
            return address;
        }
        if (!(parsed instanceof Inet6Address)) {
            return parsed.getHostAddress();
        }
        return prefix(parsed.getAddress(), PREFIX_HEXTETS);
    }

    /** The /48 an IPv6 address counts under beside its /64; nothing for an IPv4 address or text that is none. */
    public static Optional<String> wide(String address) {
        Objects.requireNonNull(address, "address");
        if (address.indexOf(':') < 0) {
            return Optional.empty();
        }
        InetAddress parsed = literal(address);
        if (!(parsed instanceof Inet6Address)) {
            return Optional.empty();
        }
        return Optional.of(prefix(parsed.getAddress(), WIDE_HEXTETS));
    }

    private static @Nullable InetAddress literal(String address) {
        try {
            // A literal is never looked up in DNS.
            return InetAddress.ofLiteral(address);
        } catch (IllegalArgumentException notALiteral) {
            return null;
        }
    }

    private static String prefix(byte[] bytes, int hextets) {
        StringJoiner prefix = new StringJoiner(":", "", "::/" + (hextets * 16));
        for (int hextet = 0; hextet < hextets; hextet++) {
            int value = ((bytes[2 * hextet] & 0xff) << 8) | (bytes[2 * hextet + 1] & 0xff);
            prefix.add(Integer.toHexString(value));
        }
        return prefix.toString();
    }
}
