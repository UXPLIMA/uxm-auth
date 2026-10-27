package com.uxplima.auth.domain.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AttemptsTest {

    @Test
    void eachFailureUsesOneAttemptUntilNoneAreLeft() {
        Attempts attempts = Attempts.fresh(3).failed().failed();

        assertThat(attempts.left()).isEqualTo(1);
        assertThat(attempts.exhausted()).isFalse();
        assertThat(attempts.failed().exhausted()).isTrue();
        assertThat(attempts.failed().failed().left()).isZero();
    }

    @Test
    void aPlayerAlwaysHasAtLeastOneAttempt() {
        assertThatThrownBy(() -> Attempts.fresh(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
