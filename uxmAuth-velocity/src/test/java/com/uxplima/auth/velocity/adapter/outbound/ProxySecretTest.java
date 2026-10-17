package com.uxplima.auth.velocity.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;

import com.uxplima.auth.domain.network.NetworkSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The proxy makes the network's secret on its first start, keeps it in a file, and reads that file after. */
final class ProxySecretTest {

    @Test
    @DisplayName("the first start writes a fresh secret, and every later start reads the same one")
    void theFirstStartWritesTheSecret(@TempDir Path folder) throws IOException {
        ProxySecret.Loaded first = ProxySecret.loadOrCreate(folder, "");
        ProxySecret.Loaded second = ProxySecret.loadOrCreate(folder, "");

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.secret()).isEqualTo(first.secret());
        assertThat(Files.readString(first.file(), StandardCharsets.UTF_8).trim())
                .isEqualTo(first.secret().encoded());
    }

    @Test
    @DisplayName("a secret written in config.conf wins over the file, so an operator can set their own")
    void theConfiguredSecretWins(@TempDir Path folder) {
        String own = "o".repeat(40);

        ProxySecret.Loaded loaded = ProxySecret.loadOrCreate(folder, own);

        assertThat(loaded.secret()).isEqualTo(NetworkSecret.parse(own));
        assertThat(loaded.created()).isFalse();
    }

    @Test
    @DisplayName("the secret file is readable by the proxy's own user alone")
    void theSecretFileIsPrivate(@TempDir Path folder) throws IOException {
        ProxySecret.Loaded loaded = ProxySecret.loadOrCreate(folder, "");

        assertThat(Files.getPosixFilePermissions(loaded.file()))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
    }
}
