package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.hash.HashFormats;
import com.uxplima.auth.domain.password.Password;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlysisSettingsTest {

    private static final String BLYSIS = """
            blysis {
              enabled = true
              jdbc-url = "jdbc:postgresql://10.19.38.7:5432/blysis"
              username = "uxmauth"
              password = "hunter22-database"
            }
            """;

    @TempDir
    Path folder;

    private PluginSettings settings(String config) throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, config, StandardCharsets.UTF_8);
        return new PluginSettings(file);
    }

    @Test
    void blysisIsOffAtFirst() throws IOException {
        assertThat(settings("").current().blysis().enabled()).isFalse();
    }

    @Test
    void theWebsiteTableAndBlysisAreNeverOnTogether() {
        assertThatThrownBy(() -> settings(BLYSIS + "website-table { enabled = true, hash = \"bcrypt\" }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("website-table and blysis are both on");
    }

    @Test
    void blysisWritesBcryptWhateverTheAlgorithmSays() throws IOException {
        PluginSettings.Snapshot snapshot = settings(
                        BLYSIS + "hashing { algorithm = \"argon2id\", bcrypt { cost = 4 } }\n")
                .current();

        assertThat(snapshot.hashers().currentAlgorithm()).isEqualTo(HashAlgorithm.BCRYPT);
        assertThat(HashFormats.recognise(snapshot.hashers().hash(Password.of("hunter22"))))
                .contains(HashAlgorithm.BCRYPT);
    }

    @Test
    void blysisSetToScryptWritesScrypt() throws IOException {
        PluginSettings.Snapshot snapshot =
                settings(BLYSIS.replace("}\n", "  hash = \"scrypt\"\n}\n")).current();

        assertThat(snapshot.hashers().currentAlgorithm()).isEqualTo(HashAlgorithm.SCRYPT);
    }

    @Test
    void blysisOnNeedsItsDatabaseAndAnAddressWithTheName() {
        assertThatThrownBy(() -> settings("blysis { enabled = true }\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blysis.jdbc-url");
        assertThatThrownBy(() -> settings(BLYSIS.replace("}\n", "  email = \"players@example.com\"\n}\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blysis.email");
        assertThatThrownBy(() -> settings(BLYSIS.replace("}\n", "  hash = \"md5\"\n}\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blysis.hash");
    }

    @Test
    void theAddressAMemberGetsHoldsTheNameInLowerCase() throws IOException {
        BlysisSettings blysis = settings(BLYSIS.replace("}\n", "  email = \"{name}@oyuncu.example.com\"\n}\n"))
                .current()
                .blysis();

        assertThat(blysis.emailOf("Steve")).isEqualTo("steve@oyuncu.example.com");
    }

    @Test
    void theDatabasePasswordIsNeverShown() throws IOException {
        assertThat(settings(BLYSIS).current().blysis().toString()).doesNotContain("hunter22-database");
    }
}
