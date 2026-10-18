package com.uxplima.auth.paper.adapter.outbound.log;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Hides the arguments of a password command in the line the server logs for it.
 *
 * <p>The server writes {@code Notch issued server command: /login hunter2} for every command a player runs.
 * This keeps the command and replaces everything after it with {@code ***}. A label is matched under any
 * namespace, because {@code /authme:login} carries a password just as {@code /login} does.
 */
public final class PasswordMask {

    /** What the server writes before every command a player runs. */
    private static final String ISSUED = "issued server command: /";

    /** What stands in for the arguments. */
    static final String HIDDEN = "***";

    /**
     * The branches of a command that carry a password after their word, as staff setting one: every label of the
     * command and every word of those branches, in lower case.
     */
    public record Branches(Set<String> labels, Set<String> words) {
        /** No command has such a branch. */
        public static final Branches NONE = new Branches(Set.of(), Set.of());

        public Branches {
            labels = Set.copyOf(labels);
            words = Set.copyOf(words);
        }
    }

    private final Supplier<Set<String>> labels;
    private final Supplier<Branches> branches;

    /** @param labels every name and alias of the password commands, in lower case */
    public PasswordMask(Supplier<Set<String>> labels) {
        this(labels, () -> Branches.NONE);
    }

    /**
     * @param labels every name and alias of the password commands, in lower case
     * @param branches the branches of other commands that carry a password
     */
    public PasswordMask(Supplier<Set<String>> labels, Supplier<Branches> branches) {
        this.labels = Objects.requireNonNull(labels, "labels");
        this.branches = Objects.requireNonNull(branches, "branches");
    }

    /** The line with a password command's arguments hidden, or the line itself. */
    public String mask(String line) {
        Objects.requireNonNull(line, "line");
        int issued = line.indexOf(ISSUED);
        if (issued < 0) {
            return line;
        }
        int start = issued + ISSUED.length();
        int space = line.indexOf(' ', start);
        if (space < 0) {
            return line;
        }
        String label = line.substring(start, space).toLowerCase(Locale.ROOT);
        String bare = label.substring(label.indexOf(':') + 1);
        if (labels.get().contains(bare)) {
            return line.substring(0, space) + " " + HIDDEN;
        }
        Branches carrying = branches.get();
        return carrying.labels().contains(bare) ? branch(line, space, carrying.words()) : line;
    }

    /** The line with what follows a password-carrying branch hidden, or the line itself for any other branch. */
    private static String branch(String line, int space, Set<String> words) {
        int next = line.indexOf(' ', space + 1);
        if (next < 0) {
            return line;
        }
        String word = line.substring(space + 1, next).toLowerCase(Locale.ROOT);
        return words.contains(word) ? line.substring(0, next) + " " + HIDDEN : line;
    }
}
