package com.uxplima.auth.application.migrate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.auth.application.port.DatabaseCopy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The accounts move to another database only when nobody plays and the target holds nothing of ours. */
final class MoveDatabaseTest {

    private final List<String> done = new ArrayList<>();
    private final Map<String, Long> source = new LinkedHashMap<>(Map.of("uxmauth_account", 3L, "uxmauth_meta", 2L));
    private final Map<String, Long> target = new LinkedHashMap<>(Map.of("uxmauth_account", 0L, "uxmauth_meta", 0L));
    private int online;
    private boolean configured = true;
    private Runnable duringCopy = () -> {};

    private final DatabaseCopy copy = new DatabaseCopy() {
        @Override
        public Map<String, Long> sourceRows() {
            done.add("count the source");
            return Map.copyOf(source);
        }

        @Override
        public Map<String, Long> targetRows() {
            done.add("count the target");
            return Map.copyOf(target);
        }

        @Override
        public Map<String, Long> copy() {
            done.add("copy");
            Map<String, Long> written = Map.copyOf(source);
            duringCopy.run();
            target.putAll(written);
            return written;
        }

        @Override
        public void close() {
            done.add("close");
        }
    };

    private MoveDatabase.Moved move() {
        return new MoveDatabase(() -> online, () -> {
                    done.add("open");
                    return configured ? Optional.of(copy) : Optional.empty();
                })
                .move();
    }

    @Test
    @DisplayName("an empty target takes every row, the counts agree, and the target is given back")
    void anEmptyTargetTakesEverything() {
        MoveDatabase.Moved moved = move();

        assertThat(moved).isEqualTo(new MoveDatabase.Moved.Done(Map.of("uxmauth_account", 3L, "uxmauth_meta", 2L)));
        assertThat(done)
                .containsExactly("open", "count the target", "count the source", "copy", "count the source", "close");
    }

    @Test
    @DisplayName("a player online refuses the move before the target is even opened")
    void aPlayerOnlineRefuses() {
        online = 1;

        assertThat(move()).isEqualTo(new MoveDatabase.Moved.Refused(MoveDatabase.Refusal.PLAYERS_ONLINE));
        assertThat(done).isEmpty();
    }

    @Test
    @DisplayName("no target named refuses the move")
    void noTargetRefuses() {
        configured = false;

        assertThat(move()).isEqualTo(new MoveDatabase.Moved.Refused(MoveDatabase.Refusal.NO_TARGET));
    }

    @Test
    @DisplayName("a target that holds a row of ours is refused with nothing written, so a second move is refused too")
    void aTargetWithRowsIsRefused() {
        assertThat(move()).isInstanceOf(MoveDatabase.Moved.Done.class);
        done.clear();

        assertThat(move()).isEqualTo(new MoveDatabase.Moved.Refused(MoveDatabase.Refusal.TARGET_HOLDS_ROWS));
        assertThat(done).containsExactly("open", "count the target", "close");
    }

    @Test
    @DisplayName("a source that changed while it was copied is reported, because the copy missed what changed")
    void aChangeDuringTheCopyIsReported() {
        duringCopy = () -> source.put("uxmauth_account", 4L);

        assertThat(move())
                .isEqualTo(new MoveDatabase.Moved.Changed(
                        Map.of("uxmauth_account", 4L, "uxmauth_meta", 2L),
                        Map.of("uxmauth_account", 3L, "uxmauth_meta", 2L)));
    }
}
