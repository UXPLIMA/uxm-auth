package com.uxplima.auth.domain.name;

import java.util.Locale;
import java.util.Objects;

/**
 * A player's name exactly as they typed it, and the key two spellings of it share.
 *
 * <p>The key folds with {@link Locale#ROOT}. A server that runs in Turkish folds {@code I} to a dotless
 * {@code ı} under its own locale, so {@code ILIKE} and {@code ilike} would become two accounts.
 */
public record PlayerName(String value) {

    public PlayerName {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("a name is never blank");
        }
    }

    /** The case-free key under which one name is held once. */
    public String key() {
        return value.toLowerCase(Locale.ROOT);
    }
}
