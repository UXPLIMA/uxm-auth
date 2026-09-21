package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every word under a command can be renamed, aliased and disabled, and the shipped file shows it.
 *
 * <p>{@code commands.conf} opens by promising an operator they can rename, alias or disable any command
 * without touching the code. A branch keeps that promise only when it carries {@code @FromConfig}: the
 * library registers its replacer for that annotation alone and rewrites it into the {@code @Subcommand}
 * the DSL expects. A branch written as a bare {@code @Subcommand} is compiled in, so the file cannot
 * reach it. The rename does nothing, {@code enabled = false} leaves the word in the tree, and neither
 * says anything.
 *
 * <p>The empty path is the exception and the only one. {@code @Subcommand("")} marks the method that
 * runs when a player types the command and nothing after it, so it names no word and there is nothing
 * for a file to rename.
 *
 * <p>Showing it matters as much as allowing it. A branch the shipped file never lists is a branch an
 * operator cannot find, which is what {@code EverySettingIsShownTest} rules against one level down.
 */
final class EveryBranchIsConfigurableTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path SHIPPED = Path.of("src", "main", "resources", "commands.conf");

    /** A branch the file cannot reach: a bare {@code @Subcommand} naming a word. */
    private static final Pattern COMPILED_IN = Pattern.compile("@Subcommand\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    /** A branch the file can reach, by the key it is named under. */
    private static final Pattern CONFIGURED = Pattern.compile("@FromConfig\\(\\s*value\\s*=\\s*\"([^\"]+)\"");

    /** The constant a command handler declares for the key its root is named under. */
    private static final Pattern ROOT_KEY = Pattern.compile("String KEY = \"([^\"]+)\"");

    /**
     * One key under a {@code subcommands} block of the shipped file.
     *
     * <p>A word under a word is written two ways in this estate and both are read here. uxmCrates writes
     * the key with a hyphen and the name with a space, {@code key-give { name = "key give" }}; uxmSkills
     * writes the key itself with a space, quoted, {@code "lang set" { name = "lang set" }}. The library
     * matches the key against what the annotation declares either way, so a reader that knew only one of
     * them reported a branch as hidden that was shown on the line above.
     */
    private static final Pattern SHOWN =
            Pattern.compile("(?m)^\\s{4,}(?:\"([a-z][a-z0-9 -]*)\"|([a-z][a-z0-9-]*))\\s*\\{");

    @Test
    @DisplayName("no branch is compiled in, so the file can reach every one of them")
    void nobranchIsCompiledIn() throws IOException {
        List<String> compiledIn = new ArrayList<>();
        for (Path file : sources()) {
            Matcher branch = COMPILED_IN.matcher(Files.readString(file, StandardCharsets.UTF_8));
            while (branch.find()) {
                compiledIn.add(file.getFileName() + ": " + branch.group(1));
            }
        }

        assertThat(compiledIn)
                .describedAs("commands.conf promises an operator can rename, alias or disable any command."
                        + " A bare @Subcommand is compiled in, so the rename does nothing and"
                        + " enabled = false leaves the word in the tree. Write @FromConfig.")
                .isEmpty();
    }

    @Test
    @DisplayName("every branch the code declares is shown in the shipped file")
    void everybranchIsShown() throws IOException {
        Set<String> shown = shownKeys();
        List<String> hidden = new ArrayList<>();
        for (String key : declaredKeys()) {
            if (!shown.contains(key)) {
                hidden.add(key);
            }
        }

        assertThat(hidden)
                .describedAs("A branch no shipped file lists is a branch an operator cannot find. It works"
                        + " for whoever reads the source and nobody else knows it can be renamed.")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothsidesAreRead() throws IOException {
        assertThat(declaredKeys())
                .describedAs("this plugin has branches, so a scan that finds none is a broken scan")
                .isNotEmpty();
        assertThat(shownKeys())
                .describedAs("the shipped file lists this plugin's branches, so a read that finds none is"
                        + " a broken read")
                .isNotEmpty();
    }

    /**
     * Every key a branch is named under, which is the word on the left in the shipped file.
     *
     * <p>A root is named under its own key at the top of the file rather than under {@code subcommands},
     * so the roots are taken out. They are recognised by the constant every command handler declares for
     * the purpose rather than by a list of names, so this reads the same in every plugin.
     */
    private static Set<String> declaredKeys() throws IOException {
        Set<String> roots = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        for (Path file : sources()) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher root = ROOT_KEY.matcher(text);
            while (root.find()) {
                roots.add(root.group(1));
            }
            Matcher branch = CONFIGURED.matcher(text);
            while (branch.find()) {
                keys.add(branch.group(1));
            }
        }
        keys.removeAll(roots);
        return keys;
    }

    private static Set<String> shownKeys() throws IOException {
        Set<String> keys = new LinkedHashSet<>();
        String body = Files.readString(SHIPPED, StandardCharsets.UTF_8);
        StringBuilder withoutComments = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (!line.strip().startsWith("#")) {
                withoutComments.append(line).append('\n');
            }
        }
        Matcher key = SHOWN.matcher(withoutComments.toString());
        while (key.find()) {
            keys.add(key.group(1) != null ? key.group(1) : key.group(2));
        }
        return keys;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }
}
