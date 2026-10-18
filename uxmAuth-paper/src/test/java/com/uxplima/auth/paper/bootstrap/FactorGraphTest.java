package com.uxplima.auth.paper.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import com.uxplima.auth.adapter.outbound.SecretSeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which key seals the second factor's secrets: the server's own file alone, the network's secret on a network. */
final class FactorGraphTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    Path folder;

    @Test
    @DisplayName("alone, the key file is made and used; on a network, the network secret seals")
    void aloneTheFileOnANetworkTheSecret() {
        String sealedAlone = FactorGraph.seal(false, "", folder).seal(SECRET);
        assertThat(folder.resolve(FactorGraph.KEY_FILE)).exists();

        String sealedOnNetwork = FactorGraph.seal(true, "net secret", folder).seal(SECRET);

        assertThat(SecretSeal.fromPassphrase("net secret").open(sealedOnNetwork))
                .isEqualTo(SECRET);
        assertThatThrownBy(() -> SecretSeal.fromPassphrase("net secret").open(sealedAlone))
                .isInstanceOf(SecretSeal.Unreadable.class);
    }

    @Test
    @DisplayName("a server alone that joins a network still opens what it sealed alone")
    void aServerThatJoinsANetworkKeepsItsSecrets() {
        String sealedAlone = FactorGraph.seal(false, "", folder).seal(SECRET);

        assertThat(FactorGraph.seal(true, "net secret", folder).open(sealedAlone))
                .isEqualTo(SECRET);
    }

    @Test
    @DisplayName("a network member that never ran alone makes no key file")
    void aMemberMakesNoKeyFile() {
        FactorGraph.seal(true, "net secret", folder);

        assertThat(folder.resolve(FactorGraph.KEY_FILE)).doesNotExist();
    }
}
