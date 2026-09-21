package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when a permission node names a rank.
 *
 * <p>No rank is named in our code. How a number varies by rank is that the operator writes a permission
 * node, names it whatever their server calls that group, and the plugin reads a number off whichever one
 * the player holds. A node we wrote that carried a rank name would be a rank we chose for them, and a
 * number they cannot reach without a source change.
 *
 * <p>The question is asked of nodes rather than of the whole tree, and that is the point. uxmJobs guards
 * its whole source against a list of rank words and passes, because its content happens not to use them.
 * The same list run over the others catches an enchantment rarity called legendary, a crate called elite,
 * a comment about Treasury answering a subscriber, and uxmSkills' own paragraph explaining that it names
 * no vip node. None of those is a rank: they are content an operator renames, and a guard that refused
 * them would refuse the vocabulary it exists to protect. A permission node is the one place where such a
 * word can only mean the name of a group somebody bought.
 */
final class NoRankInANodeTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /**
     * The words a server calls its paying groups.
     *
     * <p>Nothing here is a Minecraft word. A node is not a material list, but the rule stays the same: what
     * is refused is a word that can only be the name of a group somebody bought.
     */
    private static final List<String> RANK_WORDS = List.of(
            "vip",
            "premium",
            "elite",
            "mvp",
            "deluxe",
            "platinum",
            "titan",
            "legendary",
            "prestige",
            "patron",
            "donator",
            "donor",
            "subscriber",
            "supporter");

    /**
     * A string literal that is one of our permission nodes.
     *
     * <p>Every node this estate writes begins with the plugin's own space, {@code uxmcrates.},
     * {@code uxmjobs.} and so on, and that prefix is what tells a node from the two other dotted literals
     * a plugin is full of: a message key and a configuration path. Matching every dotted literal read a
     * cosmetic rarity called {@code rarity.legendary} and a battle pass's {@code pass.track-premium} as
     * ranks, which is the vocabulary this guard exists to protect rather than to refuse.
     */
    private static final Pattern NODE_LITERAL = Pattern.compile("\"(uxm[a-z]+(?:\\.[a-z0-9_*-]+)+\\.?)\"");

    /** A configuration line whose key says the value is a node or a node prefix. */
    private static final Pattern NODE_SETTING = Pattern.compile(
            "^\\s*[a-z0-9-]*(?:permission|node)[a-z0-9-]*\\s*[:=]\\s*\"?([^\"\\n#]+)", Pattern.MULTILINE);

    /**
     * The nodes that carry one of the words and are not ranks, by name, with the reason.
     *
     * <p>A word on the list above can be this plugin's own mechanic rather than a group somebody bought,
     * and where it is, the node is named here. Nothing is exempt by pattern: an exemption is one node,
     * written down, and the test below fails when a name here matches nothing in the source, because an
     * entry that matches nothing is a hole nobody can see.
     */
    private static final List<String> NOT_A_RANK = List.of();

    /** A word, not a fragment of one. {@code elite} must not match {@code delete}. */
    private static final Pattern WORD_BOUNDARY = Pattern.compile("(?<![a-z0-9])(%s)(?![a-z0-9])");

    @Test
    @DisplayName("no permission node this plugin writes names a rank")
    void nonodeInTheSourceNamesARank() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : filesUnder(SOURCE, ".java")) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (int index = 0; index < lines.size(); index++) {
                Matcher literal = NODE_LITERAL.matcher(lines.get(index));
                while (literal.find()) {
                    if (NOT_A_RANK.contains(literal.group(1))) {
                        continue;
                    }
                    for (String word : rankWordsIn(literal.group(1))) {
                        offenders.add(SOURCE.relativize(file) + ":" + (index + 1) + " writes the node "
                                + literal.group(1) + ", which names the rank " + word);
                    }
                }
            }
        }

        assertThat(offenders)
                .describedAs("A rank is the operator's own. Read a number off whichever node they wrote and"
                        + " named, and never write the name of a group into one.")
                .isEmpty();
    }

    @Test
    @DisplayName("no shipped file names a rank where it names a node")
    void noshippedNodeSettingNamesARank() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path file : filesUnder(RESOURCES, ".conf", ".yml")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            Matcher setting = NODE_SETTING.matcher(text);
            while (setting.find()) {
                if (NOT_A_RANK.contains(setting.group(1).trim())) {
                    continue;
                }
                for (String word : rankWordsIn(setting.group(1))) {
                    offenders.add(RESOURCES.relativize(file) + " sets a node to "
                            + setting.group(1).trim() + ", which names the rank " + word);
                }
            }
        }

        assertThat(offenders)
                .describedAs("A shipped node that names a rank has chosen the operator's groups for them."
                        + " Ship the prefix and let them name what hangs off it.")
                .isEmpty();
    }

    @Test
    @DisplayName("every exempted node is still written somewhere, so the set cannot rot")
    void theexemptionsAreStillReal() throws IOException {
        List<String> unused = new ArrayList<>(NOT_A_RANK);
        for (Path file : filesUnder(SOURCE, ".java")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            unused.removeIf(text::contains);
        }
        for (Path file : filesUnder(RESOURCES, ".conf", ".yml")) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            unused.removeIf(text::contains);
        }

        assertThat(unused)
                .describedAs("an exemption that matches nothing exempts nothing and hides that the guard"
                        + " has widened. Remove it, or correct it to the node it was renamed to")
                .isEmpty();
    }

    @Test
    @DisplayName("the guard sees a rank in a node, and leaves the content vocabulary alone")
    void theguardCanFail() {
        assertThat(rankWordsIn("uxmjobs.slots.vip")).containsExactly("vip");
        assertThat(rankWordsIn("uxmcrates.reroll.premium")).containsExactly("premium");
        // Content an operator renames, which is not a rank and must not read as one.
        assertThat(rankWordsIn("deepslate_diamond_ore")).isEmpty();
        assertThat(rankWordsIn("netherite_ingot")).isEmpty();
        // A word that merely contains one is not one.
        assertThat(rankWordsIn("the request was deleted")).isEmpty();
        assertThat(rankWordsIn("elitewood")).isEmpty();
        // A dotted literal that is not a node is a message key or a config path, and neither is read.
        assertThat(nodesIn("MENU_RARITY(\"rarity.legendary\", \"Legendary\")")).isEmpty();
        assertThat(nodesIn("config.getInt(\"prestige.maximum\", 10)")).isEmpty();
        assertThat(nodesIn("hasPermission(\"uxmcrates.reroll.premium\")")).containsExactly("uxmcrates.reroll.premium");
    }

    /** The nodes one line of source writes, which is what the first test reads. */
    private static List<String> nodesIn(String line) {
        List<String> found = new ArrayList<>();
        Matcher literal = NODE_LITERAL.matcher(line);
        while (literal.find()) {
            found.add(literal.group(1));
        }
        return found;
    }

    private static List<Path> filesUnder(Path root, String... suffixes) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> endsWithAny(path.toString(), suffixes))
                    .sorted()
                    .toList();
        }
    }

    private static boolean endsWithAny(String name, String... suffixes) {
        for (String suffix : suffixes) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> rankWordsIn(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (String word : RANK_WORDS) {
            Matcher matcher = Pattern.compile(WORD_BOUNDARY.pattern().formatted(Pattern.quote(word)))
                    .matcher(lower);
            if (matcher.find()) {
                found.add(word);
            }
        }
        return found;
    }
}
