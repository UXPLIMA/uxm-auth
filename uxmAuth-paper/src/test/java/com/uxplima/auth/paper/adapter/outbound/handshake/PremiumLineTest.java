package com.uxplima.auth.paper.adapter.outbound.handshake;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.domain.premium.NameRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The start says who the handshake proves, as the operator's rule decides, never more. */
final class PremiumLineTest {

    @Test
    @DisplayName("under opt-in the start says only a player who typed /premium is proved")
    void optInSaysOnlyThoseWhoAsked() {
        assertThat(ServerHellos.onLine(NameRule.OPT_IN)).contains("/premium").doesNotContain("auto-login is on");
    }

    @Test
    @DisplayName("under the other rules the start says paid accounts are proved at the door")
    void theOtherRulesSayAutoLoginIsOn() {
        for (NameRule rule : new NameRule[] {NameRule.AUTOMATIC, NameRule.STRICT, NameRule.DOMAIN}) {
            assertThat(ServerHellos.onLine(rule)).contains("Premium auto-login is on");
        }
    }
}
