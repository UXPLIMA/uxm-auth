package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmlib.config.FeatureFiles;
import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The largest features keep their settings in their own files, and a config.conf from before moves out once. */
class FeatureFilesTest {

    @TempDir
    Path folder;

    private Path old() throws IOException {
        Path file = folder.resolve("config.conf");
        Files.writeString(file, """
                language {
                  default = "tr"
                }

                # Mine, not the shipped words.
                first {
                  value = 7
                }

                second {
                  value = 8
                }

                menu {
                  rows = 6
                }
                """);
        return file;
    }

    @Test
    void aBlockMovesWithItsCommentAndTheRestStays() throws IOException {
        Path file = old();

        assertThat(FeatureFiles.moveOut(file, List.of("first", "second"))).containsExactly("first.conf", "second.conf");

        assertThat(Files.readString(folder.resolve("first.conf"))).startsWith("# Mine, not the shipped words.");
        HoconConfig config = HoconConfig.load(file);
        assertThat(config.nodeAt("first").virtual()).isTrue();
        assertThat(config.nodeAt("second").virtual()).isTrue();
        assertThat(config.getString("language.default", "")).isEqualTo("tr");
        assertThat(config.getInt("menu.rows", 0)).isEqualTo(6);
        assertThat(Files.readString(file)).doesNotContain("Mine");
    }

    @Test
    void aFeatureFileThatIsThereIsLeftAlone() throws IOException {
        Path file = old();
        Files.writeString(folder.resolve("first.conf"), "first { value = 1 }\n");

        assertThat(FeatureFiles.moveOut(file, List.of("first", "second"))).containsExactly("second.conf");

        assertThat(Files.readString(folder.resolve("first.conf"))).isEqualTo("first { value = 1 }\n");
        assertThat(HoconConfig.load(file).getInt("first.value", 0)).isEqualTo(7);
    }

    @Test
    void aReaderFindsAMovedKeyAtThePathItAlwaysHad() throws IOException {
        Path file = old();
        FeatureFiles.moveOut(file, List.of("first", "second"));

        HoconConfig config = HoconConfig.load(file);
        FeatureFiles.readInto(config, folder, List.of("first", "second"));

        assertThat(config.getInt("first.value", 0)).isEqualTo(7);
        assertThat(config.getInt("second.value", 0)).isEqualTo(8);
    }

    @Test
    void eachShippedFeatureHasItsOwnFileAndIsNotInConfig() throws IOException {
        copy("config.conf");
        for (String block : PluginSettings.FEATURE_FILES) {
            copy(FeatureFiles.file(block));
        }
        HoconConfig shipped = HoconConfig.load(folder.resolve("config.conf"));
        for (String block : PluginSettings.FEATURE_FILES) {
            assertThat(shipped.nodeAt(block).virtual()).as(block).isTrue();
        }

        FeatureFiles.readInto(shipped, folder, PluginSettings.FEATURE_FILES);

        for (String block : PluginSettings.FEATURE_FILES) {
            assertThat(shipped.nodeAt(block).virtual()).as(block).isFalse();
        }
    }

    @Test
    void aFeatureFileGetsTheKeysAReleaseAdds() throws IOException {
        String block = PluginSettings.FEATURE_FILES.get(0);
        Files.writeString(folder.resolve(FeatureFiles.file(block)), block + " {}\n");

        assertThat(FeatureFiles.bringUpToDate(folder, List.of(block), getClass().getClassLoader()))
                .containsExactly(FeatureFiles.file(block));

        assertThat(HoconConfig.load(folder.resolve(FeatureFiles.file(block)))
                        .nodeAt(block)
                        .childrenMap())
                .isNotEmpty();
    }

    private void copy(String resource) throws IOException {
        try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/" + resource), resource)) {
            Files.write(folder.resolve(resource), in.readAllBytes());
        }
    }
}
