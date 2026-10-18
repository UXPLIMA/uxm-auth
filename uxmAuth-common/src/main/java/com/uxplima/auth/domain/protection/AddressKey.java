package com.uxplima.auth.domain.protection;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * The key an address is counted under. An IPv4 address is its own; an IPv6 address counts by its /64, because a
 * home is handed a whole /64 and could otherwise fail from a new address every time.
 */
public final class AddressKey {

    private static final int PREFIX_HEXTETS = 4;

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
        InetAddress parsed;
        try {
            // A literal is never looked up in DNS.
            parsed = InetAddress.ofLiteral(address);
        } catch (IllegalArgumentException notALiteral) {
            return address;
        }
        if (!(parsed instanceof Inet6Address)) {
            return parsed.getHostAddress();
        }
        byte[] bytes = parsed.getAddress();
        StringJoiner prefix = new StringJoiner(":", "", "::/64");
        for (int hextet = 0; hextet < PREFIX_HEXTETS; hextet++) {
            int value = ((bytes[2 * hextet] & 0xff) << 8) | (bytes[2 * hextet + 1] & 0xff);
            prefix.add(Integer.toHexString(value));
        }
        return prefix.toString();
    }
}
