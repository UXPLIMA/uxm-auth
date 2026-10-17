package com.uxplima.auth.application.premium;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.uxplima.auth.application.port.NameSource;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.TurnedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A name is asked of the web sources in the operator's order, and the answer is kept for a while. */
final class PremiumLookupTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final PremiumAnswer PREMIUM = new PremiumAnswer.Premium(NOTCH, "Notch");

    private final TurnedClock clock = new TurnedClock(AuthFixture.NOW);
    private final List<String> asked = new ArrayList<>();

    private NameSource source(String id, PremiumAnswer answer) {
        return name -> {
            asked.add(id + ":" + name.value());
            return answer;
        };
    }

    private PremiumLookup lookup(NameSource... sources) {
        return new PremiumLookup(() -> List.of(sources), () -> Duration.ofMinutes(30), clock);
    }

    @Test
    @DisplayName("the sources are asked in the operator's order and the first answer stands")
    void theFirstAnswerStands() {
        PremiumLookup lookup = lookup(source("mojang", PREMIUM), source("playerdb", new PremiumAnswer.Cracked()));

        assertThat(lookup.lookup(new PlayerName("Notch"))).isEqualTo(PREMIUM);
        assertThat(asked).containsExactly("mojang:Notch");
    }

    @Test
    @DisplayName("a source that cannot answer passes the name to the next one")
    void aSourceThatCannotAnswerPassesItOn() {
        PremiumLookup lookup = lookup(
                source("mojang", new PremiumAnswer.Unknown()),
                source("playerdb", new PremiumAnswer.Cracked()),
                source("ashcon", PREMIUM));

        assertThat(lookup.lookup(new PlayerName("Steve"))).isEqualTo(new PremiumAnswer.Cracked());
        assertThat(asked).containsExactly("mojang:Steve", "playerdb:Steve");
    }

    @Test
    @DisplayName("when every source fails the answer is unknown, and the next lookup asks again")
    void everySourceFailingIsUnknownAndNotKept() {
        PremiumLookup lookup =
                lookup(source("mojang", new PremiumAnswer.Unknown()), source("playerdb", new PremiumAnswer.Unknown()));

        assertThat(lookup.lookup(new PlayerName("Steve"))).isEqualTo(new PremiumAnswer.Unknown());
        lookup.lookup(new PlayerName("Steve"));
        assertThat(asked).hasSize(4);
    }

    @Test
    @DisplayName("an answer is kept for the time the operator set, under the name in any case")
    void anAnswerIsKeptForTheSetTime() {
        PremiumLookup lookup = lookup(source("mojang", PREMIUM));

        lookup.lookup(new PlayerName("Notch"));
        clock.turn(Duration.ofMinutes(30).minusSeconds(1));
        assertThat(lookup.lookup(new PlayerName("NOTCH"))).isEqualTo(PREMIUM);
        assertThat(asked).hasSize(1);

        clock.turn(Duration.ofSeconds(1));
        lookup.lookup(new PlayerName("Notch"));
        assertThat(asked).hasSize(2);
    }

    @Test
    @DisplayName("a cracked answer is kept too, so a burst of one name asks the web once")
    void aCrackedAnswerIsKept() {
        PremiumLookup lookup = lookup(source("mojang", new PremiumAnswer.Cracked()));

        lookup.lookup(new PlayerName("Steve"));
        lookup.lookup(new PlayerName("Steve"));

        assertThat(asked).hasSize(1);
    }

    @Test
    @DisplayName("answers past their time are let go, so random names do not grow the memory without end")
    void oldAnswersAreLetGo() {
        PremiumLookup lookup = lookup(source("mojang", new PremiumAnswer.Cracked()));
        for (int i = 0; i < 300; i++) {
            lookup.lookup(new PlayerName("Name" + i));
        }
        clock.turn(Duration.ofMinutes(31));

        lookup.lookup(new PlayerName("Fresh"));

        assertThat(lookup.kept()).isEqualTo(1);
    }
}
