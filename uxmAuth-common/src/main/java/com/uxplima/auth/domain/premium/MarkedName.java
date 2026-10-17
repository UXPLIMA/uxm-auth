package com.uxplima.auth.domain.premium;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.name.PlayerName;

/**
 * The name a cracked account moves to when the paid owner of its name proves it: the name, the operator's mark,
 * and a number when that is taken too.
 *
 * <p>A Minecraft name is 16 characters at most, so a result that would pass it shortens the name before the
 * mark. The mark and the number are what tell the player it moved, so they are never cut.
 */
public final class MarkedName {

    /** The longest name the game sends. */
    public static final int LONGEST = 16;

    /** The characters Paper's own name check lets through. */
    private static final Pattern PAPER_NAME = Pattern.compile("[A-Za-z0-9_]+");

    /** Past this many numbers every name has been tried that anybody would want. */
    private static final int TRIES = 10_000;

    private MarkedName() {}

    /** The first free name for an account that moves away from {@code name}. */
    public static PlayerName freeFor(PlayerName name, String mark, Predicate<PlayerName> taken) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(mark, "mark");
        Objects.requireNonNull(taken, "taken");
        if (mark.isEmpty() || mark.length() > LONGEST - 3) {
            throw new IllegalArgumentException(
                    "the mark must be from 1 to " + (LONGEST - 3) + " characters, got: \"" + mark + "\"");
        }
        for (int number = 1; number <= TRIES; number++) {
            String suffix = number == 1 ? mark : mark + number;
            String base = name.value().substring(0, Math.min(name.value().length(), LONGEST - suffix.length()));
            PlayerName candidate = new PlayerName(base + suffix);
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("no free name is left for " + name.value() + " with the mark " + mark);
    }

    /** Whether a mark passes Paper's name check, which refuses any other character while it is on. */
    public static boolean paperAccepts(String mark) {
        return PAPER_NAME.matcher(Objects.requireNonNull(mark, "mark")).matches();
    }
}
