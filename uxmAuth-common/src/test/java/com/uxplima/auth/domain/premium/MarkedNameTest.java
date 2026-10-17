package com.uxplima.auth.domain.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The name a cracked account moves to when its paid owner proves the name it held. */
final class MarkedNameTest {

    private static PlayerName free(String name, String mark, Set<String> taken) {
        return MarkedName.freeFor(new PlayerName(name), mark, candidate -> taken.contains(candidate.key()));
    }

    @Test
    @DisplayName("the mark follows the name")
    void theMarkFollowsTheName() {
        assertThat(free("Notch", "_", Set.of())).isEqualTo(new PlayerName("Notch_"));
    }

    @Test
    @DisplayName("when the marked name is taken a number follows it, the first one free")
    void aNumberFollowsATakenMarkedName() {
        assertThat(free("Notch", "_", Set.of("notch_", "notch_2"))).isEqualTo(new PlayerName("Notch_3"));
    }

    @Test
    @DisplayName("a result past 16 characters shortens the name before the mark, never the mark or the number")
    void aLongResultShortensTheName() {
        assertThat(free("Abcdefghijklmnop", "_", Set.of())).isEqualTo(new PlayerName("Abcdefghijklmno_"));
        assertThat(free("Abcdefghijklmnop", "_", Set.of("abcdefghijklmno_")))
                .isEqualTo(new PlayerName("Abcdefghijklmn_2"));
        assertThat(free("Abcdefghijklmnop", "_x", Set.of())).isEqualTo(new PlayerName("Abcdefghijklmn_x"));
    }

    @Test
    @DisplayName("a number that grows a digit shortens the name one more")
    void aWiderNumberShortensMore() {
        Set<String> taken = new java.util.HashSet<>();
        taken.add("abcdefghijklmno_");
        for (int i = 2; i <= 9; i++) {
            taken.add("abcdefghijklmn_" + i);
        }

        assertThat(free("Abcdefghijklmnop", "_", taken)).isEqualTo(new PlayerName("Abcdefghijklm_10"));
    }

    @Test
    @DisplayName("an empty mark, or one too long to leave room for a name, is refused")
    void aMarkWithNoRoomIsRefused() {
        assertThatThrownBy(() -> free("Notch", "", Set.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> free("Notch", "_".repeat(14), Set.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("only letters, digits and the underscore pass Paper's name check")
    void paperAcceptsOnlyItsOwnCharacters() {
        assertThat(MarkedName.paperAccepts("_")).isTrue();
        assertThat(MarkedName.paperAccepts("_c2")).isTrue();
        assertThat(MarkedName.paperAccepts("+")).isFalse();
        assertThat(MarkedName.paperAccepts("-")).isFalse();
    }
}
