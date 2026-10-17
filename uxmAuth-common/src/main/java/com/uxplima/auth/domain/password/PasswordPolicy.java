package com.uxplima.auth.domain.password;

import java.nio.CharBuffer;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The operator's rules for a password.
 *
 * <p>{@code maxBytes} is the limit the current hash reads, zero when it reads everything. It is set by the
 * settings when BCrypt is the current algorithm, and it is counted in UTF-8 bytes because that is what BCrypt
 * counts.
 */
public record PasswordPolicy(int minLength, int maxLength, Pattern allowed, Set<CharClass> required, int maxBytes) {

    public PasswordPolicy {
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(required, "required");
        required = required.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(required));
        if (minLength < 1 || maxLength < minLength || maxBytes < 0) {
            throw new IllegalArgumentException("the lengths must be positive and the maximum at least the minimum");
        }
    }

    /** What the policy says about one password. */
    public PasswordVerdict check(Password password) {
        Objects.requireNonNull(password, "password");
        int length = password.length();
        if (length < minLength) {
            return new PasswordVerdict.TooShort(minLength);
        }
        if (length > maxLength) {
            return new PasswordVerdict.TooLong(maxLength);
        }
        char[] chars = password.chars();
        try {
            if (!allowed.matcher(CharBuffer.wrap(chars)).matches()) {
                return new PasswordVerdict.BadCharacters();
            }
            Set<CharClass> missing = missing(chars);
            if (!missing.isEmpty()) {
                return new PasswordVerdict.Missing(missing);
            }
        } finally {
            java.util.Arrays.fill(chars, '\0');
        }
        if (maxBytes > 0) {
            byte[] bytes = password.utf8();
            int count = bytes.length;
            java.util.Arrays.fill(bytes, (byte) 0);
            if (count > maxBytes) {
                return new PasswordVerdict.TooLongForTheHash(maxBytes);
            }
        }
        return new PasswordVerdict.Accepted();
    }

    private Set<CharClass> missing(char[] chars) {
        Set<CharClass> missing = EnumSet.noneOf(CharClass.class);
        for (CharClass needed : required) {
            boolean found = CharBuffer.wrap(chars).codePoints().anyMatch(needed::matches);
            if (!found) {
                missing.add(needed);
            }
        }
        return missing;
    }
}
