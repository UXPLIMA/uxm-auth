package com.uxplima.auth.domain.factor;

import java.io.ByteArrayOutputStream;
import java.util.Locale;
import java.util.Objects;

/** RFC 4648 Base32, the alphabet an authenticator app reads a secret in, written without padding. */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS = 5;
    private static final int MASK = 0x1f;

    private Base32() {}

    public static String encode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        StringBuilder out = new StringBuilder((bytes.length * 8 + BITS - 1) / BITS);
        int buffer = 0;
        int held = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xff);
            held += 8;
            while (held >= BITS) {
                out.append(ALPHABET.charAt((buffer >> (held - BITS)) & MASK));
                held -= BITS;
            }
        }
        if (held > 0) {
            out.append(ALPHABET.charAt((buffer << (BITS - held)) & MASK));
        }
        return out.toString();
    }

    /** Reads a secret as a person types it: either case, spaces and padding left out. */
    public static byte[] decode(String text) {
        Objects.requireNonNull(text, "text");
        String cleaned = text.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream(cleaned.length() * BITS / 8);
        int buffer = 0;
        int held = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int value = ALPHABET.indexOf(cleaned.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character: " + cleaned.charAt(i));
            }
            buffer = (buffer << BITS) | value;
            held += BITS;
            if (held >= 8) {
                out.write((buffer >> (held - 8)) & 0xff);
                held -= 8;
            }
        }
        return out.toByteArray();
    }
}
