package com.uxplima.auth.paper.adapter.inbound.screen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;

import org.junit.jupiter.api.Test;

class UnansweredPressesTest {

    private static final DialogSpec LOGIN =
            new DialogSpec(Component.text("Login"), List.of(), List.of(), List.of(), false);

    private final RecordingShower shower = new RecordingShower();
    private final List<LogRecord> logged = new ArrayList<>();
    private final UnansweredPresses unanswered = new UnansweredPresses(shower, logger());

    @Test
    void aPressTheGateDoesNotRunClosesTheScreenAndIsLogged() {
        Audience client = Audience.empty();

        unanswered.dropped(client, "Steve", OptionalLong.of(7), UnansweredPresses.Dropped.OTHER_CONNECTION);

        assertThat(shower.closed).containsExactly(client);
        assertThat(shower.shown).isEmpty();
        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.getLevel()).isEqualTo(Level.INFO);
            assertThat(entry.getMessage()).contains("Steve", "OTHER_CONNECTION", "opening 7");
        });
    }

    @Test
    void aLoginWhoseSessionIsGoneShowsTheLoginScreenAgainAndIsLogged() {
        Audience client = Audience.empty();

        unanswered.gone(client, "Steve", OptionalLong.of(3), LOGIN);

        assertThat(shower.shown).containsExactly(LOGIN);
        assertThat(shower.closed).isEmpty();
        assertThat(logged).singleElement().satisfies(entry -> {
            assertThat(entry.getLevel()).isEqualTo(Level.INFO);
            assertThat(entry.getMessage()).contains("Steve", "opening 3");
        });
    }

    @Test
    void theGateDropsAPressOnlyFromAConnectionThatIsNotTheWaitingOne() {
        assertThat(UnansweredPresses.dropFor(false, false)).contains(UnansweredPresses.Dropped.NOT_WAITING);
        assertThat(UnansweredPresses.dropFor(true, false)).contains(UnansweredPresses.Dropped.OTHER_CONNECTION);
        assertThat(UnansweredPresses.dropFor(true, true)).isEmpty();
    }

    private Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord entry) {
                logged.add(entry);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        });
        return logger;
    }

    /** Remembers what it was asked to show and to take away. */
    private static final class RecordingShower implements ScreenShower {
        final List<DialogSpec> shown = new ArrayList<>();
        final List<Audience> closed = new ArrayList<>();

        @Override
        public void show(Audience who, DialogSpec spec) {
            shown.add(spec);
        }

        @Override
        public void close(Audience who) {
            closed.add(who);
        }
    }
}
