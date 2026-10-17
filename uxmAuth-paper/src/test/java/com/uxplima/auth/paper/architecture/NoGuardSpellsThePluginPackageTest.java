package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.uxplima.auth.paper.testing.SourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No guard writes this plugin's package name out as a string of its own.
 *
 * <p>{@code scripts/new-plugin.sh} rewrites two spellings, {@code com.uxplima.auth} and
 * {@code com/uxplima/auth}. A guard that builds a path piece by piece writes neither:
 *
 * <pre>{@code
 *   Path.of("src", "main", "java", "com", "uxplima", "template", "bootstrap", "PluginModule.java")
 * }</pre>
 *
 * <p>The word sits alone in a string argument, the rename walks past it, and the new plugin's guard
 * points at a package that does not exist. uxmPlots met exactly that on its first build: four failing
 * tests in two guards, on code nobody had touched.
 *
 * <p><b>It hid because the template builds green.</b> The defect exists only in the output of the
 * rename, and nothing builds that output except a new plugin on its first day. That is the second time
 * this shape has shipped: the older note repaired the slash spelling and left this one.
 *
 * <p>So the rule is not a third spelling for the script to chase. It is this test, and
 * {@link SourcePaths}, which derives the package from the class that asks. This test is the half that
 * survives: somebody will write a literal path again, and it will fail here rather than in a plugin
 * that does not exist yet.
 *
 * <p>The check is narrow on purpose. A test may name its own package in a {@code package} line and in
 * an import, which the rename does see. What it may not do is spell the plugin segment as a quoted
 * string.
 */
final class NoGuardSpellsThePluginPackageTest {

    private static final Path TESTS = Path.of("src", "test", "java");

    /** The file that is allowed to know the shape, because it is the one that derives it. */
    private static final String THE_DERIVER = "SourcePaths.java";

    @Test
    @DisplayName("no test spells the plugin package as a quoted path segment")
    void notestSpellsThePackage() throws IOException {
        String segment = '"' + lastSegmentOf(SourcePaths.pluginPackage(getClass())) + '"';
        List<String> offenders = new ArrayList<>();
        for (Path file : tests()) {
            if (file.getFileName().toString().equals(THE_DERIVER)) {
                continue;
            }
            if (withoutComments(Files.readString(file, StandardCharsets.UTF_8)).contains(segment)) {
                offenders.add(file.getFileName().toString());
            }
        }

        assertThat(offenders)
                .describedAs(
                        "new-plugin.sh rewrites com.uxplima.%s and com/uxplima/%s and neither is this."
                                + " Use SourcePaths.of(getClass(), ...) so the package is derived.",
                        lastSegmentOf(SourcePaths.pluginPackage(getClass())),
                        lastSegmentOf(SourcePaths.pluginPackage(getClass())))
                .isEmpty();
    }

    @Test
    @DisplayName("the scan reads the tests, so it cannot pass by finding nothing")
    void thescanReadsTheTests() throws IOException {
        assertThat(tests())
                .describedAs("this plugin has tests, so a scan that finds none is reading the wrong place")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the derived package is the one the sources really live under")
    void thederivedPackageIsReal() {
        assertThat(SourcePaths.of(getClass(), "bootstrap"))
                .describedAs("a derivation that pointed at the wrong package would make every guard that"
                        + " uses it fail with a missing file, which is a worse way to learn it")
                .exists();
    }

    /**
     * The source with its comments removed.
     *
     * <p>A javadoc that shows the mistake has to spell it, and this test's own does. Scanning the
     * comments would make the description of the rule the first thing to break it, which is a guard
     * arguing with its own documentation.
     */
    private static String withoutComments(String body) {
        return body.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static String lastSegmentOf(String packageName) {
        int dot = packageName.lastIndexOf('.');
        return dot < 0 ? packageName : packageName.substring(dot + 1);
    }

    private static List<Path> tests() throws IOException {
        try (Stream<Path> files = Files.walk(TESTS)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }
}
