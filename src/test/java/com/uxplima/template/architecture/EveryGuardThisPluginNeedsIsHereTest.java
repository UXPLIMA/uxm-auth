package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * This plugin carries the guards its own content asks for.
 *
 * <p>Every guard in this directory is copied from plugin to plugin by hand, and a plugin that grows a
 * feature after the copy never gets the guard that covers it. That has happened three times that anybody
 * noticed: uxmGlow gained a command branch and not {@code EveryBranchIsConfigurableTest}, the whole
 * estate gained the action engine and not {@code EveryBossBarComesDownTest}, and every plugin written
 * from the template starts without the three that the template itself does not need.
 *
 * <p>So the rule is the other way round here. Nothing is required of a plugin that does not have the
 * thing: a plugin with no action engine needs no boss bar guard, and adding one would leave a guard whose
 * own non-vacuity test cannot pass. What is required is that a plugin **which has the thing** has the
 * guard that reads it.
 *
 * <p>This is the guard that survives the copy, because it is the one that notices the copy was
 * incomplete.
 */
final class EveryGuardThisPluginNeedsIsHereTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    private static final Path GUARDS = Path.of("src", "test", "java");

    /** A sound named in a shipped file, in either of the two spellings this estate writes. */
    private static final Pattern SOUND =
            Pattern.compile("(?m)(\\[sound\\]|[a-z-]*sounds?\\s*[=:]\\s*\"?)[A-Za-z0-9_.:]*[._][A-Za-z0-9_.:]*");

    /**
     * A line of the engine's grammar in a shipped file, which is what an effect list is made of. The
     * quotes are part of it: a bracket on its own is a table in a comment as often as it is an action.
     */
    private static final Pattern ACTION_LINE = Pattern.compile("\"\\[[a-z-]+\\][^\"]*\"");

    /** A material named in a shipped file, which is a screaming-case word under a material-ish key. */
    private static final Pattern MATERIAL =
            Pattern.compile("(?m)(material|icon|item)[a-z-]*\\s*=\\s*\"?[A-Z][A-Z0-9_]{2,}");

    @Test
    @DisplayName("a plugin that builds an action context guards the boss bar")
    void thebossBarGuardIsHere() throws IOException {
        if (!sourceHolds("ActionContext.builder(")) {
            return;
        }
        assertThat(guardExists("EveryBossBarComesDownTest"))
                .describedAs("[bossbar] puts a bar up and something has to take it down, and an unwired"
                        + " context throws after the bar is already on the player")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships a sound name guards the sound names")
    void thesoundGuardIsHere() throws IOException {
        if (!shippedMatches(SOUND)) {
            return;
        }
        assertThat(guardExists("EveryShippedSoundExistsTest"))
                .describedAs("a sound the server does not have is silence, and silence is what a click that"
                        + " was meant to answer sounds like")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships a material name guards the material names")
    void thematerialGuardIsHere() throws IOException {
        if (!shippedMatches(MATERIAL)) {
            return;
        }
        assertThat(guardExists("EveryShippedMaterialExistsTest"))
                .describedAs("a material this version does not have is a window slot that draws nothing")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin with a command branch guards that the branch can be renamed")
    void thebranchGuardIsHere() throws IOException {
        if (!sourceHolds("@Subcommand(") && !sourceHolds("@FromConfig(")) {
            return;
        }
        assertThat(guardExists("EveryBranchIsConfigurableTest"))
                .describedAs("every command can be renamed, aliased and disabled, and a branch compiled in"
                        + " is a branch an operator cannot touch")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that builds an action context guards the command sinks")
    void thecommandSinkGuardIsHere() throws IOException {
        if (!sourceHolds("ActionContext.builder(")) {
            return;
        }
        assertThat(guardNamed("EveryCommandVerbHasASinkTest"))
                .describedAs("[console] and [player] dispatch through a sink the caller wires, and the"
                        + " library discarded the line when it was not. Four plugins were relying on that"
                        + " without knowing, and none of the four shipped a console line of its own")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that writes an action line guards that the line is readable")
    void thereadableLineGuardIsHere() throws IOException {
        if (!shippedMatches(ACTION_LINE)) {
            return;
        }
        assertThat(guardNamed("Readable"))
                .describedAs("a line the engine cannot read takes its whole list down and does it quietly:"
                        + " the runner falls back to the plugin's own wording and nothing looks broken."
                        + " Three plugins shipped one in a week and none of the three had this guard")
                .isTrue();
    }

    @Test
    @DisplayName("a plugin that ships a message catalogue guards that every key is read")
    void thedeadKeyGuardIsHere() throws IOException {
        if (!shipsACatalogue()) {
            return;
        }
        assertThat(guardExists("EveryKeyIsReadTest"))
                .describedAs("a key nothing sends is a line a translator was paid for and a player can"
                        + " never be shown, and the other catalogue guards cannot see it: one proves the"
                        + " languages agree with each other, the other proves a window names nothing"
                        + " unwritten. uxmMOTD shipped error.no-permission that way for months")
                .isTrue();
    }

    @Test
    @DisplayName("the scan reads this plugin, so it cannot pass by finding nothing")
    void thescanReadsThePlugin() throws IOException {
        assertThat(java())
                .describedAs("a plugin with no source is a broken read rather than a plugin with no needs")
                .isNotEmpty();
        assertThat(guards())
                .describedAs("this plugin has guards, so a scan that finds none is reading the wrong place")
                .isNotEmpty();
    }

    /**
     * Whether a guard whose name holds {@code fragment} is here.
     *
     * <p>By fragment rather than by name, because this one guard has five names across the estate:
     * EveryEffectLineIsReadableTest, EveryShippedActionIsReadableTest, EveryShippedRewardIsReadableTest,
     * EveryShippedLineIsReadableTest and EveryFeedbackLineIsReadableTest. Each was named after what its
     * own plugin calls the list, and renaming five files to satisfy a scan would be the scan wagging the
     * estate.
     */
    private static boolean guardNamed(String fragment) throws IOException {
        return guards().stream()
                .map(path -> path.getFileName().toString())
                .anyMatch(name -> name.startsWith("Every") && name.contains(fragment));
    }

    private static boolean guardExists(String name) throws IOException {
        return guards().stream().anyMatch(path -> path.getFileName().toString().equals(name + ".java"));
    }

    private static boolean sourceHolds(String token) throws IOException {
        for (Path file : java()) {
            if (withoutSpaces(Files.readString(file, StandardCharsets.UTF_8)).contains(token)) {
                return true;
            }
        }
        return false;
    }

    private static boolean shippedMatches(Pattern pattern) throws IOException {
        for (Path file : shipped()) {
            if (pattern.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The source with every space taken out, which is what a token search reads.
     *
     * <p>A guard that looks for a text fragment can be defeated by the formatter: {@code spotlessApply}
     * wraps a long chain at the dot, so {@code ActionContext.builder(} becomes two lines and the text
     * this scan wants is never in the file. The break that triggers the wrap is the same break that
     * makes the line longer, so the guard would be blind in a case close to the one it exists for.
     */
    private static String withoutSpaces(String body) {
        return body.replaceAll("\\s+", "");
    }

    private static List<Path> java() throws IOException {
        return under(SOURCE, ".java");
    }

    private static List<Path> guards() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path file : under(GUARDS, ".java")) {
            if (file.toString().replace('\\', '/').contains("/architecture/")) {
                found.add(file);
            }
        }
        return found;
    }

    /** Every file a first boot writes out, apart from the words, which name no sound and no material. */
    private static List<Path> shipped() throws IOException {
        List<Path> found = new ArrayList<>();
        for (Path file : under(RESOURCES, ".conf")) {
            if (!file.toString().replace('\\', '/').contains("/messages/")) {
                found.add(file);
            }
        }
        return found;
    }

    /** Whether this plugin ships words of its own, which is where a key nothing sends hides. */
    private static boolean shipsACatalogue() throws IOException {
        return under(RESOURCES, ".conf").stream()
                .anyMatch(path -> path.toString().replace('\\', '/').contains("/messages/"));
    }

    private static List<Path> under(Path root, String suffix) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .toList();
        }
    }
}
