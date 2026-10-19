package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.domain.premium.NameRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The start says who the handshake proves, on a server and on a proxy, as the operator's rule decides. */
final class PremiumStartLineTest {

    @Test
    @DisplayName("under opt-in only a player who typed /premium is proved, wherever the door is")
    void optInSaysOnlyThoseWhoAsked() {
        assertThat(PremiumStartLine.of(NameRule.OPT_IN, "the proxy"))
                .contains("/premium")
                .contains("the proxy")
                .doesNotContain("auto-login is on");
    }

    @Test
    @DisplayName("under the other rules paid accounts are proved at the door")
    void theOtherRulesSayAutoLoginIsOn() {
        for (NameRule rule : new NameRule[] {NameRule.AUTOMATIC, NameRule.STRICT, NameRule.DOMAIN}) {
            assertThat(PremiumStartLine.of(rule, "the proxy"))
                    .isEqualTo("Premium auto-login is on: paid accounts are proved by Mojang at the proxy.");
        }
    }
}
