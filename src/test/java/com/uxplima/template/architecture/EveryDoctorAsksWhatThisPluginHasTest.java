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
 * The doctor asks about what this plugin has.
 *
 * <p>An operator who cannot read a log has one way to ask how a plugin is, and what they may ask about
 * was written per plugin and drifted. uxmShop kept the shops, their offers, their stock and the log in a
 * database its doctor never mentioned. uxmQuests answered about its database and said nothing about the
 * four windows it draws, each of which is a file that can fail to parse. The answer an operator gets
 * should depend on what can go wrong, not on which product they bought.
 *
 * <p>So three of the checks are not this plugin's choice. A plugin with a database reports its storage, a
 * plugin that ships windows reports how many were read, and a plugin that publishes an expansion reports
 * whether the placeholder plugin is there. Everything else a doctor says is the plugin's own, and a
 * plugin that only reads another plugin's placeholders rather than publishing its own is free to report
 * that or not: uxmJoinItems does, and nothing here asks it to.
 *
 * <p>Both halves are read: the check has to exist in {@code PluginHealth}, and the composition root has to
 * hand it to the doctor. A method nobody wires is a check nobody runs.
 */
final class EveryDoctorAsksWhatThisPluginHasTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    @DisplayName("a plugin with a database reports its storage")
    void adatabaseIsReported() throws IOException {
        if (!has("db/migration")) {
            return;
        }
        assertThat(doctorSays("storage"))
                .describedAs("a database that does not answer is every other symptom at once, and the"
                        + " window that would have said so is the window that cannot draw")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships windows reports how many were read")
    void thewindowsAreReported() throws IOException {
        if (!has("menus")) {
            return;
        }
        assertThat(doctorSays("windows"))
                .describedAs("a window is a file an operator edits, and a file that did not parse is a"
                        + " menu that does not open with no line anywhere saying why")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that publishes an expansion reports whether anybody is listening")
    void theplaceholdersAreReported() throws IOException {
        if (!sources().contains("PlaceholderExpansions.registered(")) {
            return;
        }
        assertThat(doctorSays("placeholders"))
                .describedAs("our placeholders in somebody else's scoreboard read as nothing when the"
                        + " plugin behind them is not there, and nothing else shows it")
                .isTrue();
    }

    @Test
    @DisplayName("the doctor is asked something, so the three above are read against a real one")
    void thedoctorIsAskedSomething() throws IOException {
        assertThat(sources())
                .describedAs("a plugin whose composition root wires no check at all has a doctor that"
                        + " answers nothing, and the three tests above would pass by finding nothing to"
                        + " look at")
                .contains("PluginHealth.");
    }

    /** Whether the doctor both declares and wires a check of this name. */
    private static boolean doctorSays(String check) throws IOException {
        String sources = sources();
        return sources.contains("HealthCheck " + check + "(") && sources.contains("PluginHealth." + check + "(");
    }

    private static boolean has(String folder) {
        return Files.isDirectory(RESOURCES.resolve(folder));
    }

    /** Every production source of this plugin as one string. */
    private static String sources() throws IOException {
        List<String> read = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                read.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return String.join("\n", read);
    }
}
