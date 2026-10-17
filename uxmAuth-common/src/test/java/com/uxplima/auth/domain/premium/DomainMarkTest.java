package com.uxplima.auth.domain.premium;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxplima.auth.domain.name.PlayerName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The mark a cracked name gets under the domain rule, before or after it. */
final class DomainMarkTest {

    @Test
    @DisplayName("the mark goes after the name, or before it, as the operator wrote")
    void theMarkGoesWhereWritten() {
        assertThat(new DomainMark("+", false).on(new PlayerName("Notch"))).isEqualTo(new PlayerName("Notch+"));
        assertThat(new DomainMark("c_", true).on(new PlayerName("Notch"))).isEqualTo(new PlayerName("c_Notch"));
    }

    @Test
    @DisplayName("the longest cracked name is 16 characters minus the mark")
    void theLongestNameLeavesRoomForTheMark() {
        assertThat(new DomainMark("+", false).longestName()).isEqualTo(15);
        assertThat(new DomainMark("_cr", true).longestName()).isEqualTo(13);
    }

    @Test
    @DisplayName("a mark is never empty, and always leaves room for a name")
    void aMarkLeavesRoom() {
        assertThatThrownBy(() -> new DomainMark("", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainMark("x".repeat(14), false)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a mark holds only characters the server takes in a profile name: no space, nothing past ASCII")
    void aMarkHoldsCharactersTheServerTakes() {
        assertThat(new DomainMark("+", false).mark()).isEqualTo("+");
        assertThat(new DomainMark("~!", true).mark()).isEqualTo("~!");
        assertThatThrownBy(() -> new DomainMark(" ", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainMark("\u2605", false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DomainMark("\u007f", false)).isInstanceOf(IllegalArgumentException.class);
    }
}
