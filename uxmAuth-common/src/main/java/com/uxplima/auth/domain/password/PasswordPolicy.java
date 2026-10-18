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
 * counts. {@code common} lists the passwords too common to keep.
 */
public record PasswordPolicy(
        int minLength, int maxLength, Pattern allowed, Set<CharClass> required, int maxBytes, CommonPasswords common) {

    public PasswordPolicy {
        Objects.requireNonNull(allowed, "allowed");
        Objects.requireNonNull(common, "common");
        Objects.requireNonNull(required, "required");
        required = required.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(required));
        if (minLength < 1 || maxLength < minLength || maxBytes < 0) {
            throw new IllegalArgumentException("the lengths must be positive and the maximum at least the minimum");
        }
    }

    /** Rules every password passes: a login under them is never held to replace its password. */
    public static final PasswordPolicy ANY =
            new PasswordPolicy(1, Integer.MAX_VALUE, Pattern.compile("(?s).*"), Set.of(), 0);

    /** The rules with no list of common passwords. */
    public PasswordPolicy(int minLength, int maxLength, Pattern allowed, Set<CharClass> required, int maxBytes) {
        this(minLength, maxLength, allowed, required, maxBytes, CommonPasswords.none());
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
        if (!fitsTheHash(password)) {
            return new PasswordVerdict.TooLongForTheHash(maxBytes);
        }
        return common.contains(password) ? new PasswordVerdict.Common() : new PasswordVerdict.Accepted();
    }

    /** Whether the hash reads the whole password, counted in the UTF-8 bytes BCrypt counts. */
    private boolean fitsTheHash(Password password) {
        if (maxBytes == 0) {
            return true;
        }
        byte[] bytes = password.utf8();
        int count = bytes.length;
        java.util.Arrays.fill(bytes, (byte) 0);
        return count <= maxBytes;
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
