package com.uxplima.auth.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class AttemptsTest {

    @Test
    @DisplayName("each failure counts once and the last allowed one exhausts")
    void countsDown() {
        Attempts attempts = Attempts.fresh(3);

        assertThat(attempts.left()).isEqualTo(3);
        assertThat(attempts.failed().failed().left()).isEqualTo(1);
        assertThat(attempts.failed().failed().exhausted()).isFalse();
        assertThat(attempts.failed().failed().failed().exhausted()).isTrue();
    }

    @Test
    @DisplayName("a limit below one is refused, because nobody could ever log in")
    void needsOneAttempt() {
        assertThatThrownBy(() -> Attempts.fresh(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
