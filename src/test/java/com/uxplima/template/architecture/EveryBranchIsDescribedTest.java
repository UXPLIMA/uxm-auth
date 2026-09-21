package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every branch of a command says what it does.
 *
 * <p>The help page is a surface like any window, and it is the only one a player reaches without being
 * told what to type. The library draws a line per branch, with the description beside it and on the
 * hover; a branch with none draws its name and nothing else, and what a player sees then is a list of
 * words they have to guess at. It read that way in five plugins of the estate and not in the others,
 * which is the drift this closes.
 *
 * <p>The bare branch is exempt. {@code @Subcommand("")} is the command itself, and the command's own
 * description is on the class, where the help header reads it.
 */
final class EveryBranchIsDescribedTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** A branch, however it is spelled, with everything up to the closing bracket of the annotation. */
    private static final Pattern BRANCH = Pattern.compile("@(?:FromConfig|Subcommand)\\(([^)]*)\\)", Pattern.DOTALL);

    /** The name a branch answers to, which is what a reader of the failure needs to find it. */
    private static final Pattern NAME = Pattern.compile("\"([^\"]*)\"");

    @Test
    @DisplayName("every branch of every command carries a description")
    void everybranchCarriesADescription() throws IOException {
        List<String> bare = new ArrayList<>();

        for (Path file : commandSources()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            Matcher branch = BRANCH.matcher(source);
            while (branch.find()) {
                String declared = branch.group(1);
                if (declared.contains("description")) {
                    continue;
                }
                Matcher named = NAME.matcher(declared);
                String name = named.find() ? named.group(1) : declared.strip();
                if (name.isEmpty()) {
                    // The bare branch is the command itself and the class says what that does.
                    continue;
                }
                bare.add(file.getFileName() + ": " + name);
            }
        }

        assertThat(bare)
                .describedAs("A branch with no description draws its name on the help page and nothing"
                        + " else, and a player reading that has to guess what it does.")
                .isEmpty();
    }

    @Test
    @DisplayName("the branches are found, so this cannot pass by reading nothing")
    void thebranchesAreFound() throws IOException {
        int found = 0;
        for (Path file : commandSources()) {
            Matcher branch = BRANCH.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (branch.find()) {
                found++;
            }
        }

        assertThat(found)
                .describedAs("this plugin has commands, so a scan that finds no branch is a broken scan")
                .isGreaterThan(1);
    }

    /** Every source that declares a command, which is where a branch can be. */
    private static List<Path> commandSources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .filter(EveryBranchIsDescribedTest::declaresABranch)
                    .toList();
        }
    }

    private static boolean declaresABranch(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            return source.contains("@Subcommand(") || source.contains("@FromConfig(");
        } catch (IOException unreadable) {
            throw new java.io.UncheckedIOException("could not read " + file, unreadable);
        }
    }
}
