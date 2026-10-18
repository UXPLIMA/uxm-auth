package com.uxplima.auth.domain.password;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The passwords the operator lists as too common to keep, compared without regard to case. A typed password is
 * never made into a string to be looked up: it is compared character by character against the listed passwords
 * of its own length, and its characters are wiped after.
 */
public final class CommonPasswords {

    private static final String COMMENT = "#";

    private final Map<Integer, Set<String>> byLength;
    private final int size;

    private CommonPasswords(Map<Integer, Set<String>> byLength, int size) {
        this.byLength = byLength;
        this.size = size;
    }

    /** No list: nothing is too common. */
    public static CommonPasswords none() {
        return new CommonPasswords(Map.of(), 0);
    }

    /** The lines of a list file: one password a line, blank lines and lines starting with # left out. */
    public static CommonPasswords of(List<String> lines) {
        Objects.requireNonNull(lines, "lines");
        Map<Integer, List<String>> byLength = new HashMap<>();
        lines.stream()
                .map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
                .map(line -> line.toLowerCase(Locale.ROOT))
                .distinct()
                .forEach(line -> byLength.computeIfAbsent(line.length(), length -> new java.util.ArrayList<>())
                        .add(line));
        int size = byLength.values().stream().mapToInt(List::size).sum();
        Map<Integer, Set<String>> frozen = new HashMap<>();
        byLength.forEach((length, listed) -> frozen.put(length, Set.copyOf(listed)));
        return new CommonPasswords(Map.copyOf(frozen), size);
    }

    /** How many passwords are listed. */
    public int size() {
        return size;
    }

    /** Whether the password is on the list, in any case. */
    public boolean contains(Password password) {
        Objects.requireNonNull(password, "password");
        if (size == 0) {
            return false;
        }
        char[] typed = password.chars();
        try {
            Set<String> sameLength = byLength.getOrDefault(typed.length, Set.of());
            return sameLength.stream().anyMatch(listed -> same(typed, listed));
        } finally {
            Arrays.fill(typed, '\0');
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CommonPasswords those && those.byLength.equals(byLength);
    }

    @Override
    public int hashCode() {
        return byLength.hashCode();
    }

    /** Never the passwords themselves: a list of them is a list of what players type. */
    @Override
    public String toString() {
        return "CommonPasswords[" + size + "]";
    }

    private static boolean same(char[] typed, String listed) {
        for (int i = 0; i < typed.length; i++) {
            if (Character.toLowerCase(typed[i]) != listed.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
