package com.uxplima.auth.domain.protection;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Objects;

/**
 * An address as it may leave the server, in a webhook or anywhere else a stranger reads: the first two parts of
 * an IPv4 address or the first two groups of an IPv6 one, enough to tell two networks apart and never enough to
 * find a home.
 */
public final class AddressMask {

    private static final String HIDDEN = "x";

    private AddressMask() {}

    public static String of(String address) {
        Objects.requireNonNull(address, "address");
        if (!AddressKey.isAddress(address)) {
            return HIDDEN;
        }
        // A literal is never looked up in DNS.
        InetAddress parsed = InetAddress.ofLiteral(address);
        byte[] bytes = parsed.getAddress();
        if (parsed instanceof Inet4Address) {
            return (bytes[0] & 0xff) + "." + (bytes[1] & 0xff) + ".x.x";
        }
        int first = ((bytes[0] & 0xff) << 8) | (bytes[1] & 0xff);
        int second = ((bytes[2] & 0xff) << 8) | (bytes[3] & 0xff);
        return Integer.toHexString(first) + ":" + Integer.toHexString(second) + ":x";
    }
}
