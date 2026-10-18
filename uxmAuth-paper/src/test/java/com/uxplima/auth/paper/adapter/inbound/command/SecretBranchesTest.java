package com.uxplima.auth.paper.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.auth.paper.testing.Modules;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The admin branches that carry a password, read from commands.conf so a renamed one is still hidden. */
final class SecretBranchesTest {

    private final ConfiguredCommands shipped =
            ConfiguredCommands.load(Modules.commonResources().resolve("commands.conf"));

    @Test
    @DisplayName("every label of the admin command and every word of a password branch is read, and no other branch")
    void theWordsAreRead() {
        assertThat(SecretBranches.labels(shipped)).contains("uxmauth", "auth");
        assertThat(SecretBranches.words(shipped))
                .contains("setpassword", "sifreata", "forceregister", "zorlakayit")
                .doesNotContain("info", "unregister");
    }
}
