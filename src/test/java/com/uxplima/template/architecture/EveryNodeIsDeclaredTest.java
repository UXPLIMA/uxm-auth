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
 * Every permission node this plugin asks about is declared in {@code paper-plugin.yml}.
 *
 * <p>An undeclared node is not a node that fails. It is a node that works and cannot be found: it does not appear
 * in a permission manager's completion, it carries no description an operator can read, and it has no default of
 * its own, so the server falls back to op and a node meant for everybody quietly belongs to nobody.
 *
 * <p>Only uxmSkills declared its nodes. The other four asked about between twelve and twenty five each and
 * declared none, which is how this guard came to be written.
 *
 * <p>A literal ending in a dot is a prefix rather than a node: what goes under it is either a piece of content,
 * declared beside the content that ships, or a node the operator names themselves, which we may not name for
 * them. Those are the plugin's two dynamic families and both are exempt here.
 */
final class EveryNodeIsDeclaredTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path DESCRIPTOR = Path.of("src", "main", "resources", "paper-plugin.yml");

    /** The plugin's own node family, as it is written in a string literal. */
    private static final Pattern NODE = Pattern.compile("\"(uxmtemplate\\.[a-z0-9._-]+)\"");

    /** One declared node: two spaces, the node, a colon, at the top level of the permissions block. */
    private static final Pattern DECLARED = Pattern.compile("^ {2}([a-z0-9._*-]+):", Pattern.MULTILINE);

    @Test
    @DisplayName("every node the code asks about is declared, with a description and a default")
    void everyNodeIsDeclared() throws IOException {
        Set<String> declared = declaredNodes();
        List<String> undeclared = new ArrayList<>();

        for (String node : nodesInCode()) {
            if (node.endsWith(".")) {
                continue;
            }
            if (!declared.contains(node)) {
                undeclared.add(node);
            }
        }

        assertThat(undeclared)
                .describedAs("An undeclared node works and cannot be found, and it defaults to op rather than"
                        + " to whatever it was meant to default to.")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothsidesAreRead() throws IOException {
        // Neither test above can fail when a scan comes back empty, and a scan comes back empty the day
        // somebody renames the node family and leaves the pattern behind. It happened: this guard was
        // copied into uxmMOTD with uxmParkour's prefix still in it and passed twice over four undeclared
        // nodes. A guard that cannot fail reports that by passing.
        assertThat(nodesInCode())
                .describedAs("this plugin asks about permission nodes, so a scan that finds none is a"
                        + " broken scan: check that the pattern still names this plugin's own family")
                .isNotEmpty();
        assertThat(declaredNodes())
                .describedAs("this plugin declares its nodes, so a descriptor read that finds none is a"
                        + " broken read")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the descriptor declares no node this plugin never asks about")
    void thedescriptorDeclaresNothingExtra() throws IOException {
        Set<String> used = nodesInCode();
        List<String> unknown = new ArrayList<>();

        for (String node : declaredNodes()) {
            if (used.contains(node) || underAPrefix(node, used) || isAParentOf(node, used)) {
                continue;
            }
            unknown.add(node);
        }

        assertThat(unknown)
                .describedAs("A node an operator can grant that nothing reads is a promise the code does not keep.")
                .isEmpty();
    }

    /**
     * Whether this declared node is the wildcard parent of nodes the code does ask about.
     *
     * <p>A parent is how a descriptor says "and everything under here": an operator grants
     * {@code admin.*} once rather than fourteen lines. It reads nothing itself, so it is not a promise the code
     * fails to keep, as long as there is something under it.
     */
    private static boolean isAParentOf(String node, Set<String> used) {
        if (!node.endsWith(".*")) {
            return false;
        }
        String family = node.substring(0, node.length() - 1);
        return used.stream().anyMatch(literal -> literal.startsWith(family));
    }

    /** Whether this declared node is one member of a family the code builds nodes under. */
    private static boolean underAPrefix(String node, Set<String> used) {
        for (String literal : used) {
            if (literal.endsWith(".") && node.startsWith(literal)) {
                return true;
            }
        }
        return false;
    }

    /** Every node written as a literal anywhere in this plugin's own source. */
    private static Set<String> nodesInCode() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher node = NODE.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (node.find()) {
                    found.add(node.group(1));
                }
            }
        }
        return found;
    }

    /** Every node the descriptor's permissions block declares. */
    private static Set<String> declaredNodes() throws IOException {
        String text = Files.readString(DESCRIPTOR, StandardCharsets.UTF_8);
        int block = text.indexOf("permissions:");
        if (block < 0) {
            return Set.of();
        }
        Set<String> found = new LinkedHashSet<>();
        Matcher declared = DECLARED.matcher(text.substring(block));
        while (declared.find()) {
            found.add(declared.group(1));
        }
        return found;
    }
}
