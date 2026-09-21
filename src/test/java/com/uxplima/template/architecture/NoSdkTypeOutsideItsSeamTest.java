package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Nothing here names a type that belongs to a plugin the server may not have.
 *
 * <p>A class that names an absent SDK throws {@code NoClassDefFoundError} the moment it links, which is
 * why every optional plugin in this estate is reached through the library: the library holds the type and
 * the presence check together, past its own guard, and this plugin holds neither.
 *
 * <p>uxmSkills had one: its placeholder publisher extended PlaceholderAPI's own
 * {@code PlaceholderExpansion}, so the bootstrap had to ask the plugin manager whether PlaceholderAPI was
 * installed before it dared build one, and that check asked the wrong question as well. Nothing here does
 * it, and this is what keeps the next one from starting.
 */
final class NoSdkTypeOutsideItsSeamTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** The packages of the plugins we integrate with. None of them is a compile dependency. */
    private static final List<String> SDK_PACKAGES =
            List.of("me.clip.placeholderapi", "net.milkbowl.vault", "com.sk89q.worldguard", "org.geysermc");

    @Test
    @DisplayName("no class of this plugin imports another plugin's own types")
    void nosdkTypeIsNamedHere() throws IOException {
        List<String> named = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                int number = 0;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    number++;
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    for (String sdk : SDK_PACKAGES) {
                        if (line.contains(sdk)) {
                            named.add(file.getFileName() + ":" + number + " " + line.strip());
                        }
                    }
                }
            }
        }

        assertThat(named)
                .describedAs("A class that names an absent plugin's type throws NoClassDefFoundError when it"
                        + " links. Reach it through the library, which holds the type and the presence check"
                        + " together and answers false when the plugin is not there.")
                .isEmpty();
    }
}
