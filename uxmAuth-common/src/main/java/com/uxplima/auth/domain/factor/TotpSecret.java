package com.uxplima.auth.domain.factor;

import java.util.Arrays;
import java.util.Objects;

/** The secret an authenticator app shares with the server. It keeps its own copy and never prints itself. */
public final class TotpSecret {

    /** RFC 4226 asks a shared secret of at least 128 bits and refuses one under 80. */
    private static final int SHORTEST = 10;

    private final byte[] bytes;

    public TotpSecret(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        if (bytes.length < SHORTEST) {
            throw new IllegalArgumentException(
                    "a second-factor secret needs at least " + SHORTEST + " bytes, got " + bytes.length);
        }
        this.bytes = bytes.clone();
    }

    /** A secret typed or stored as Base32. */
    public static TotpSecret fromBase32(String text) {
        return new TotpSecret(Base32.decode(Objects.requireNonNull(text, "text")));
    }

    /** A copy of the bytes, for the code and the seal. */
    public byte[] bytes() {
        return bytes.clone();
    }

    /** The secret as an app is given it. */
    public String base32() {
        return Base32.encode(bytes);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TotpSecret that && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "TotpSecret[hidden]";
    }
}
