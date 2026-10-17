package com.uxplima.auth.domain.password;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;

/**
 * A password a player typed, held as characters so it can be wiped.
 *
 * <p>A {@code String} stays in memory until the collector takes it and can end up in any log line that prints
 * an argument. This class prints nothing, and {@link #close()} zeroes it once it has been hashed or checked.
 */
public final class Password implements AutoCloseable {

    private final char[] chars;
    private volatile boolean wiped;

    private Password(char[] chars) {
        this.chars = chars;
    }

    /** A password from the text a command or a window handed over. */
    public static Password of(String typed) {
        Objects.requireNonNull(typed, "typed");
        return new Password(typed.toCharArray());
    }

    /** A copy of the characters, for a library that takes {@code char[]}. The caller wipes the copy. */
    public char[] chars() {
        return chars.clone();
    }

    /** The UTF-8 bytes, the form every hash in this plugin is computed over. The caller wipes them. */
    public byte[] utf8() {
        // A lone surrogate becomes a question mark, as String.getBytes would make it, so no input stops short.
        CharsetEncoder encoder = StandardCharsets.UTF_8
                .newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        ByteBuffer encoded = ByteBuffer.allocate((int) Math.ceil(chars.length * (double) encoder.maxBytesPerChar()));
        encoder.encode(CharBuffer.wrap(chars), encoded, true);
        encoder.flush(encoded);
        byte[] bytes = Arrays.copyOf(encoded.array(), encoded.position());
        Arrays.fill(encoded.array(), (byte) 0);
        return bytes;
    }

    /** Whether two typed passwords are the same, compared in constant time. */
    public boolean sameAs(Password other) {
        Objects.requireNonNull(other, "other");
        byte[] mine = utf8();
        byte[] theirs = other.utf8();
        try {
            return MessageDigest.isEqual(mine, theirs);
        } finally {
            Arrays.fill(mine, (byte) 0);
            Arrays.fill(theirs, (byte) 0);
        }
    }

    /** The length a player sees: one for every character, however many units it takes in memory. */
    public int length() {
        return Character.codePointCount(chars, 0, chars.length);
    }

    /** Whether {@link #close()} has run. */
    public boolean isWiped() {
        return wiped;
    }

    @Override
    public void close() {
        Arrays.fill(chars, '\0');
        wiped = true;
    }

    @Override
    public String toString() {
        return "Password[hidden]";
    }
}
