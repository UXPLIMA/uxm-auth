package com.uxplima.auth.domain.password;

/** A kind of character a password may be required to hold. */
public enum CharClass {
    LOWER,
    UPPER,
    DIGIT,
    SYMBOL;

    /** Whether this code point is of this kind. */
    public boolean matches(int codePoint) {
        return switch (this) {
            case LOWER -> Character.isLowerCase(codePoint);
            case UPPER -> Character.isUpperCase(codePoint);
            case DIGIT -> Character.isDigit(codePoint);
            case SYMBOL -> !Character.isLetterOrDigit(codePoint) && !Character.isWhitespace(codePoint);
        };
    }
}
