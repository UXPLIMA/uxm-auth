package com.uxplima.auth.paper.adapter.outbound.log;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * The line with a password command's arguments hidden, or the line itself.
     *
     * <p>The command is read word by word, with any run of whitespace between two words, and a password command is
     * looked for at every word: a line spaced twice still logs a password even when the server refuses to run it,
     * and {@code /execute ... run login} carries one as much as {@code /login}. Everything after the word that
     * opens the password is hidden.
     */
    public String mask(String line) {
        Objects.requireNonNull(line, "line");
        int issued = line.indexOf(ISSUED);
        if (issued < 0) {
            return line;
        }
        int start = issued + ISSUED.length();
        List<int[]> words = words(line, start);
        Set<String> commands = labels.get();
        Branches carrying = branches.get();
        for (int at = 0; at + 1 < words.size(); at++) {
            String bare = bare(line, words.get(at));
            if (commands.contains(bare)) {
                return hiddenAfter(line, words.get(at));
            }
            if (carrying.labels().contains(bare) && carrying.words().contains(word(line, words.get(at + 1)))) {
                return at + 2 < words.size() ? hiddenAfter(line, words.get(at + 1)) : line;
            }
        }
        return line;
    }

    /** The start and end of every word from {@code from} on, a word being a run of anything but whitespace. */
    private static List<int[]> words(String line, int from) {
        List<int[]> found = new ArrayList<>();
        int at = from;
        while (at < line.length()) {
            while (at < line.length() && Character.isWhitespace(line.charAt(at))) {
                at++;
            }
            int begin = at;
            while (at < line.length() && !Character.isWhitespace(line.charAt(at))) {
                at++;
            }
            if (at > begin) {
                found.add(new int[] {begin, at});
            }
        }
        return found;
    }

    private static String word(String line, int[] span) {
        return line.substring(span[0], span[1]).toLowerCase(Locale.ROOT);
    }

    /** The word without a namespace, as {@code /authme:login} is {@code login}. */
    private static String bare(String line, int[] span) {
        String word = word(line, span);
        return word.substring(word.indexOf(':') + 1);
    }

    private static String hiddenAfter(String line, int[] span) {
        return line.substring(0, span[1]) + " " + HIDDEN;
    }
}
