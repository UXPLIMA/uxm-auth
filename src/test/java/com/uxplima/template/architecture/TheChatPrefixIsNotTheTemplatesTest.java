package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.template.testing.SourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A plugin made from the template does not still open its chat lines with the template's name.
 *
 * <p>The prefix a line opens with is a style tag, {@code <tag:'TEMPLATE'>}, and the rename did not
 * touch it until 2026-09-22. So every plugin made from here shipped every chat line saying TEMPLATE
 * until somebody saw it on a live server: **uxmJobs shipped nine such lines and uxmCrates forty two,
 * in both languages**, and 517 tests, a full review and a product read all missed them because
 * nobody ran the command.
 *
 * <p>The repair then was a rule in the workspace's {@code scripts/check-style.sh}. That catches it,
 * and it catches it in the wrong place: **a new plugin runs its own
 * {@code scripts/check-style.sh}, which has no such rule**, and its own build, which is green. The
 * plugin is only checked once somebody runs the workspace script over the whole tree, which is days
 * later and by somebody else.
 *
 * <p>So the transformation fixes it now and this holds it. The two together are the shape the package
 * spelling needed as well: the script rewrites, and a guard inside the plugin says whether the
 * rewrite happened.
 *
 * <p><b>The template is exempt, and it works out who it is rather than being told.</b> Naming itself
 * in a string is the mistake next door: the rename would walk past the name and the exemption would
 * follow the new plugin, which is precisely the plugin the guard is for. It reads its own package
 * instead, so the template skips and everything made from it does not.
 */
final class TheChatPrefixIsNotTheTemplatesTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** The prefix the template ships, which is the one thing a plugin made from it must not keep. */
    private static final String THE_TEMPLATES_PREFIX = "TEMPLATE";

    @Test
    @DisplayName("no shipped line opens with the template's own prefix")
    void nolineOpensWithTheTemplatesPrefix() throws IOException {
        if (isTheTemplate()) {
            return;
        }
        List<String> offenders = new ArrayList<>();
        for (Path file : shipped()) {
            if (Files.readString(file, StandardCharsets.UTF_8).contains(tag())) {
                offenders.add(file.getFileName().toString());
            }
        }

        assertThat(offenders)
                .describedAs("every chat line this plugin sends would open with the word TEMPLATE, which is"
                        + " what uxmJobs and uxmCrates shipped to real servers")
                .isEmpty();
    }

    @Test
    @DisplayName("the template itself is the one plugin allowed to carry it")
    void thetemplateIsExempt() throws IOException {
        if (!isTheTemplate()) {
            return;
        }
        assertThat(shipped())
                .describedAs("the template ships the prefix on purpose, and this test is what stops it"
                        + " travelling. A template with none would prove nothing.")
                .anyMatch(file -> carries(file, tag()));
    }

    @Test
    @DisplayName("the scan reads the shipped files, so it cannot pass by finding nothing")
    void thescanReadsTheShippedFiles() throws IOException {
        assertThat(shipped())
                .describedAs("this plugin ships words, so a scan that finds no file is reading the wrong place")
                .isNotEmpty();
    }

    /** Whether this is the template, read off the package rather than from a name written down. */
    private static boolean isTheTemplate() {
        String plugin = SourcePaths.pluginPackage(TheChatPrefixIsNotTheTemplatesTest.class);
        return plugin.substring(plugin.lastIndexOf('.') + 1).equalsIgnoreCase(THE_TEMPLATES_PREFIX);
    }

    /** The style tag as a shipped file writes it, assembled so this file does not spell it whole. */
    private static String tag() {
        return "tag:'" + THE_TEMPLATES_PREFIX + "'";
    }

    private static boolean carries(Path file, String needle) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8).contains(needle);
        } catch (IOException unreadable) {
            return false;
        }
    }

    private static List<Path> shipped() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .toList();
        }
    }
}
