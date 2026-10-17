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

    private final Supplier<Set<String>> labels;

    /** @param labels every name and alias of the password commands, in lower case */
    public PasswordMask(Supplier<Set<String>> labels) {
        this.labels = Objects.requireNonNull(labels, "labels");
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
        if (!labels.get().contains(bare)) {
            return line;
        }
        return line.substring(0, space) + " " + HIDDEN;
    }
}
