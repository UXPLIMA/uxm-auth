package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The descriptor says the server this jar was built for, and not an older one.
 *
 * <p>{@code api-version} is what the server reads to decide whether a plugin is too old to load and
 * whether its materials need remapping. It said {@code 1.21} in twenty six plugins while every one of
 * them compiled against Paper 26.2, which is a claim the jar cannot keep: a method the 26.2 API added is
 * in the bytecode, and a 1.21 server that believed the line would load the plugin and throw
 * {@code NoSuchMethodError} at whoever pressed the button first.
 *
 * <p>The owner settled the question itself on 2026-09-05: the library and the plugins do not have to
 * serve anything below 26.2. So the honest line is the platform, and the platform is not written twice:
 * it is read out of the version catalogue, which is the one place a version is written.
 */
final class TheDescriptorNamesThePlatformTest {

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");

    private static final Path CATALOGUE = Path.of("gradle", "libs.versions.toml");

    /** {@code paper = "26.2.build.112-stable"}, of which the first two parts are the platform. */
    private static final Pattern PAPER = Pattern.compile("(?m)^paper\\s*=\\s*\"(\\d+)\\.(\\d+)");

    private static final Pattern DECLARED = Pattern.compile("(?m)^api-version:\\s*'([^']+)'");

    @Test
    @DisplayName("api-version is the platform the build compiles against")
    void thedescriptorNamesThePlatform() throws IOException {
        Optional<String> platform = platform();
        Optional<String> declared = declared();

        assertThat(platform)
                .describedAs("the catalogue names the Paper the build takes, and this guard cannot read it."
                        + " Either the key moved or the pattern is wrong.")
                .isPresent();
        assertThat(declared)
                .describedAs("paper-plugin.yml declares an api-version, and this guard cannot read it")
                .isPresent();
        assertThat(declared.orElseThrow())
                .describedAs("A descriptor that claims an older server than the jar was built for is a"
                        + " promise the bytecode cannot keep: the server loads the plugin and the first"
                        + " call into an API it does not have throws at a player.")
                .isEqualTo(platform.orElseThrow());
    }

    /** The platform, as the version catalogue names it: the major and the minor of the Paper version. */
    private static Optional<String> platform() throws IOException {
        Matcher paper = PAPER.matcher(Files.readString(CATALOGUE, StandardCharsets.UTF_8));
        return paper.find() ? Optional.of(paper.group(1) + "." + paper.group(2)) : Optional.empty();
    }

    private static Optional<String> declared() throws IOException {
        Matcher version = DECLARED.matcher(Files.readString(DESCRIPTOR, StandardCharsets.UTF_8));
        return version.find() ? Optional.of(version.group(1)) : Optional.empty();
    }
}
