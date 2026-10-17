package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.domain.network.Bulletin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A member with no Redis behind it hears what it publishes, and only that. */
final class LocalEventsTest {

    @Test
    @DisplayName("every listener hears a bulletin published on the same member")
    void aMemberHearsItself() {
        LocalEvents events = new LocalEvents();
        List<Bulletin> first = new ArrayList<>();
        List<Bulletin> second = new ArrayList<>();
        events.listen(first::add);
        events.listen(second::add);
        Bulletin ended = new Bulletin.SessionsEnded(UUID.randomUUID(), false);

        events.publish(ended);

        assertThat(first).containsExactly(ended);
        assertThat(second).containsExactly(ended);
    }
}
