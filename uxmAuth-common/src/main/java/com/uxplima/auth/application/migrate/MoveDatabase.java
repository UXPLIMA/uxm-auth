package com.uxplima.auth.application.migrate;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.DatabaseCopy;

/**
 * Copies the accounts to another database, for a restart onto it to complete the move.
 *
 * <p>Nobody may be playing, so nothing is written while the rows are read, and the target may hold nothing of ours,
 * so a move never mixes two servers' accounts or runs twice onto one target. The source is counted again after the
 * copy: a row that arrived meanwhile is reported, not lost in silence.
 */
public final class MoveDatabase {

    /** What a move came to. */
    public sealed interface Moved {

        /** Every table copied, with the rows each holds now at the target. */
        record Done(Map<String, Long> rows) implements Moved {
            public Done {
                rows = Map.copyOf(rows);
            }
        }

        /** Nothing was copied. */
        record Refused(Refusal why) implements Moved {}

        /** The source changed while it was copied, so the target misses what changed. */
        record Changed(Map<String, Long> source, Map<String, Long> written) implements Moved {
            public Changed {
                source = Map.copyOf(source);
                written = Map.copyOf(written);
            }
        }
    }

    /** Why a move copied nothing. */
    public enum Refusal {
        /** No target is named in the settings. */
        NO_TARGET,
        /** A player is online, and could write while the rows are read. */
        PLAYERS_ONLINE,
        /** A table of ours at the target holds a row. */
        TARGET_HOLDS_ROWS
    }

    private final IntSupplier online;
    private final Supplier<Optional<DatabaseCopy>> target;

    /**
     * @param online how many players are on the server now
     * @param target opens the target the settings name, or answers empty when they name none
     */
    public MoveDatabase(IntSupplier online, Supplier<Optional<DatabaseCopy>> target) {
        this.online = Objects.requireNonNull(online, "online");
        this.target = Objects.requireNonNull(target, "target");
    }

    /** Moves the rows, or says why not. It reads and writes both databases: off any server thread. */
    public Moved move() {
        if (online.getAsInt() > 0) {
            return new Moved.Refused(Refusal.PLAYERS_ONLINE);
        }
        Optional<DatabaseCopy> opened = target.get();
        if (opened.isEmpty()) {
            return new Moved.Refused(Refusal.NO_TARGET);
        }
        try (DatabaseCopy copy = opened.get()) {
            if (copy.targetRows().values().stream().anyMatch(rows -> rows > 0)) {
                return new Moved.Refused(Refusal.TARGET_HOLDS_ROWS);
            }
            Map<String, Long> before = copy.sourceRows();
            Map<String, Long> written = copy.copy();
            Map<String, Long> after = copy.sourceRows();
            return before.equals(written) && after.equals(written)
                    ? new Moved.Done(written)
                    : new Moved.Changed(after, written);
        }
    }
}
