package com.uxplima.auth.paper.adapter.outbound.migrate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.adapter.outbound.ImportLayout;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.auth.domain.hash.Pbkdf2Hasher;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A source that keeps its accounts in a YAML file, one entry per player id with the hash under it, is read the
 * way the table sources are. The hash was computed with Python's hashlib for the password "hunter22".
 */
final class YamlImportTest {

    private static final UUID STEVE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID STRANGER = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final String HASH =
            "pbkdf2$1000$AAECAwQFBgcICQoLDA0ODw==$cjNIKfympRSbAs88K9Rcn6XPiOL6tRYwMWZzHxqYtXg=";

    @TempDir
    Path folder;

    @Test
    @DisplayName("each id becomes an account named as the server last saw it, its hash read; an unknown id keeps the"
            + " id as its name so the import refuses and counts it")
    void entriesAreRead() throws IOException {
        Path plugins = folder.resolve("plugins");
        Files.createDirectories(plugins.resolve("Auth"));
        Files.writeString(
                plugins.resolve("Auth/players.yml"),
                STEVE + ":\n  hash: " + HASH + "\n'" + STRANGER + "':\n  hash: " + HASH + "\nnot-an-id:\n  hash: x\n");
        ImportLayout layout = java.util.Objects.requireNonNull(
                ImportLayout.shipped(folder.resolve("imports")).get("authplus"));

        List<ImportedAccount> rows;
        try (YamlImport source = new YamlImport(
                layout,
                plugins,
                id -> Optional.ofNullable(Map.of(STEVE, "Steve").get(id)))) {
            assertThat(source.key()).isEqualTo("authplus");
            rows = source.read().toList();
        }

        assertThat(rows)
                .as("an entry whose key is not an id still comes, to be refused and counted")
                .hasSize(3);
        ImportedAccount steve =
                rows.stream().filter(row -> STEVE.equals(row.id())).findFirst().orElseThrow();
        assertThat(steve.name()).isEqualTo("Steve");
        assertThat(steve.password()).isInstanceOf(LegacyHashes.Known.class);
        Hashing hashing = new Hashing(new Pbkdf2Hasher(1000), List.of());
        assertThat(hashing.verify(
                        Password.of("hunter22"),
                        ((LegacyHashes.Known) java.util.Objects.requireNonNull(steve.password())).hash()))
                .isInstanceOfAny(Verification.Right.class, Verification.RightButRehash.class);
        ImportedAccount stranger = rows.stream()
                .filter(row -> STRANGER.equals(row.id()))
                .findFirst()
                .orElseThrow();
        assertThat(stranger.name()).isEqualTo(STRANGER.toString());
    }

    @Test
    @DisplayName("a file that is not there is a failure named by its path, so the import stops and marks nothing")
    void aMissingFileFails() {
        ImportLayout layout = java.util.Objects.requireNonNull(
                ImportLayout.shipped(folder.resolve("imports")).get("authplus"));
        try (YamlImport source = new YamlImport(layout, folder, id -> Optional.empty())) {
            assertThatThrownBy(source::read).hasMessageContaining("players.yml");
        }
    }
}
