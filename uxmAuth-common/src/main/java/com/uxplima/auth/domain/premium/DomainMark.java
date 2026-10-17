package com.uxplima.auth.domain.premium;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.domain.name.PlayerName;

/**
 * The mark a cracked name gets under the domain rule, written by the operator before or after it: {@code Notch}
 * through the cracked address plays as {@code Notch+}, a name of its own, so it never reaches the paid account
 * under {@code Notch}.
 *
 * @param atStart whether the mark goes before the name rather than after it
 */
public record DomainMark(String mark, boolean atStart) {

    /** The shortest name a mark must leave room for. */
    private static final int ROOM = 3;

    public DomainMark {
        Objects.requireNonNull(mark, "mark");
        if (mark.isEmpty() || mark.length() > MarkedName.LONGEST - ROOM) {
            throw new IllegalArgumentException(
                    "the mark must be from 1 to " + (MarkedName.LONGEST - ROOM) + " characters, got: \"" + mark + "\"");
        }
        // The server refuses a profile name with a space, a control character or anything past ASCII.
        if (mark.chars().anyMatch(c -> c <= ' ' || c > '~')) {
            throw new IllegalArgumentException(
                    "the mark holds a character the server refuses in a name, got: \"" + mark + "\"");
        }
    }

    /** The name a cracked player plays under. */
    public PlayerName on(PlayerName name) {
        Objects.requireNonNull(name, "name");
        return new PlayerName(atStart ? mark + name.value() : name.value() + mark);
    }

    /** The name the player typed, from the marked one; empty when the name does not carry the mark. */
    public Optional<String> unmark(String marked) {
        Objects.requireNonNull(marked, "marked");
        if (marked.length() <= mark.length()) {
            return Optional.empty();
        }
        if (atStart) {
            return marked.startsWith(mark) ? Optional.of(marked.substring(mark.length())) : Optional.empty();
        }
        return marked.endsWith(mark)
                ? Optional.of(marked.substring(0, marked.length() - mark.length()))
                : Optional.empty();
    }

    /** The longest name a cracked player may join with, so the marked one still fits in 16 characters. */
    public int longestName() {
        return MarkedName.LONGEST - mark.length();
    }
}
