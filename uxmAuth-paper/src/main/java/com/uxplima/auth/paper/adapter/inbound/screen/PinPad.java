package com.uxplima.auth.paper.adapter.inbound.screen;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

/**
 * The digits a player has pressed on the pad, one entry per connection.
 *
 * <p>Every press replaces the entry with a new one and zeroes the arrays it replaced, so a digit lives in one
 * place at a time. Enter takes the entry out and zeroes it; leaving the pad or the server drops it the same way.
 */
public final class PinPad {

    private static final String PREFIX = "pin-";

    /** The press that takes back the last digit. */
    public static final String ERASE = PREFIX + "erase";

    /** The press that hands the code over. */
    public static final String ENTER = PREFIX + "enter";

    /** What the pad does after a press. */
    public sealed interface Step {}

    /** Show the pad again, with this many digits masked, asking for the code a second time or not. */
    public record Shown(int entered, boolean repeating) implements Step {}

    /** The code is complete: the first entry, and the second where one was asked for. */
    public record Code(String first, String second) implements Step {
        public Code {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
        }

        @Override
        public String toString() {
            return "Code[hidden]";
        }
    }

    /** What one connection pressed. The arrays are not changed once an entry holds them, only zeroed. */
    private static final class Entry {
        private final char[] digits;
        private final char @Nullable [] first;

        Entry(char[] digits, char @Nullable [] first) {
            this.digits = digits;
            this.first = first;
        }

        char[] digits() {
            return digits;
        }

        char @Nullable [] first() {
            return first;
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    /** The press of one digit. */
    public static String digit(int digit) {
        if (digit < 0 || digit > 9) {
            throw new IllegalArgumentException("A pad has the digits 0 to 9");
        }
        return PREFIX + digit;
    }

    /** Whether a press belongs to the pad. */
    public static boolean handles(String action) {
        return Objects.requireNonNull(action, "action").startsWith(PREFIX);
    }

    /**
     * Applies one press.
     *
     * @param twice whether the code is asked for a second time before it is handed over
     * @param longest the most digits the code may have
     */
    public Step press(UUID connection, String action, boolean twice, int longest) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(action, "action");
        if (ENTER.equals(action)) {
            return enter(connection, twice);
        }
        Entry after = entries.compute(connection, (id, before) -> {
            Entry current = before == null ? new Entry(new char[0], null) : before;
            Entry next = ERASE.equals(action) ? erased(current) : appended(current, action, longest);
            if (next != current) {
                Arrays.fill(current.digits(), '\0');
            }
            return next;
        });
        return new Shown(after.digits().length, after.first() != null);
    }

    /** Drops what this connection pressed, zeroed. */
    public void forget(UUID connection) {
        Entry gone = entries.remove(Objects.requireNonNull(connection, "connection"));
        if (gone != null) {
            wipe(gone);
        }
    }

    /** The digits held for a connection, as held, for a test to see them zeroed. */
    Optional<char[]> peek(UUID connection) {
        Entry entry = entries.get(connection);
        return Optional.ofNullable(entry == null ? null : entry.digits());
    }

    private Step enter(UUID connection, boolean twice) {
        AtomicReference<Step> step = new AtomicReference<>();
        entries.compute(connection, (id, current) -> {
            if (current == null || current.digits().length == 0) {
                step.set(new Shown(0, current != null && current.first() != null));
                return current;
            }
            if (twice && current.first() == null) {
                step.set(new Shown(0, true));
                return new Entry(new char[0], current.digits());
            }
            char[] first = current.first() == null ? current.digits() : current.first();
            step.set(new Code(new String(first), new String(current.digits())));
            wipe(current);
            return null;
        });
        return Objects.requireNonNull(step.get(), "step");
    }

    private static Entry erased(Entry current) {
        if (current.digits().length == 0) {
            return current;
        }
        return new Entry(Arrays.copyOf(current.digits(), current.digits().length - 1), current.first());
    }

    private static Entry appended(Entry current, String action, int longest) {
        String rest = action.substring(PREFIX.length());
        if (rest.length() != 1 || !Character.isDigit(rest.charAt(0)) || current.digits().length >= longest) {
            return current;
        }
        char[] next = Arrays.copyOf(current.digits(), current.digits().length + 1);
        next[next.length - 1] = rest.charAt(0);
        return new Entry(next, current.first());
    }

    private static void wipe(Entry entry) {
        Arrays.fill(entry.digits(), '\0');
        char[] first = entry.first();
        if (first != null) {
            Arrays.fill(first, '\0');
        }
    }
}
