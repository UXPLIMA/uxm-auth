package com.uxplima.auth.velocity.adapter.outbound.limbo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Which kit speaks to the LimboAPI on the proxy. Each kit's own test proves that its generation finds it. */
final class LimboKitsTest {

    @Test
    @DisplayName("a plugin that is no LimboAPI this uxmAuth knows has no kit")
    void anUnknownLimboApiHasNoKit() {
        assertThat(LimboKits.of(new Object())).isEmpty();
        assertThat(LimboKits.of("a string the system loader holds")).isEmpty();
    }

    @Test
    @DisplayName("the kits are where the selector loads them from")
    void theKitNamesAreTheKits() {
        assertThat(LimboKits.TWO)
                .isEqualTo(com.uxplima.auth.velocity.adapter.outbound.limbo.two.LimboTwo.class.getName());
        assertThat(LimboKits.ONE).endsWith(".limbo.one.LimboOne");
    }
}
