package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmlib.health.HealthResult;
import com.uxplima.uxmlib.health.HealthStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PluginHealthTest {

    @Test
    @DisplayName("no language file found is a failure: every line a player reads would be the code's default")
    void noLanguageIsAFailure() {
        HealthResult result = PluginHealth.languages(() -> 0).check();

        assertThat(result.status()).isEqualTo(HealthStatus.FAIL);
    }

    @Test
    @DisplayName("the languages found are counted")
    void languagesAreCounted() {
        HealthResult result = PluginHealth.languages(() -> 2).check();

        assertThat(result.status()).isEqualTo(HealthStatus.OK);
        assertThat(result.message()).contains("2");
    }
}
