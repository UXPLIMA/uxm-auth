package com.uxplima.auth.domain.premium;

import java.util.Objects;

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
    }

    /** The name a cracked player plays under. */
    public PlayerName on(PlayerName name) {
        Objects.requireNonNull(name, "name");
        return new PlayerName(atStart ? mark + name.value() : name.value() + mark);
    }

    /** The longest name a cracked player may join with, so the marked one still fits in 16 characters. */
    public int longestName() {
        return MarkedName.LONGEST - mark.length();
    }
}
