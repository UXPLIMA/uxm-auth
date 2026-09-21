package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.template.application.message.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every language file carries every key, and no file carries a key the code does not know.
 *
 * <p>A missing key falls back to the English default the enum carries, which is survivable and invisible:
 * a Turkish player reads one English line in a plugin that ships a Turkish file, and nobody finds out for
 * a month. English and Turkish are the floor, and the floor is checked.
 *
 * <p>The other direction matters as much. A key left in a file after the code stopped naming it is a line
 * a translator keeps translating for nothing, and it is the first thing that rots when a key is renamed:
 * the English file is updated by the person doing the rename and the other files are not.
 *
 * <p>How many languages there are is nobody's business here. Whatever files are in the folder are read and
 * every one of them is held to the same rule, so a language somebody adds tomorrow is covered on the day
 * they add the file.
 *
 * <p>{@code EveryKeyIsReadTest} asks the third question, which is whether the code reads a key it ships.
 * This one is about the files.
 */
final class EveryLanguageAnswersEveryKeyTest {

    private static final Path MESSAGES = Path.of("src", "main", "resources", "messages");

    /** The two languages this plugin will not ship without. Others are welcome and nothing counts them. */
    private static final List<String> FLOOR = List.of("messages_en.conf", "messages_tr.conf");

    @Test
    @DisplayName("English and Turkish are both here, because they are the floor")
    void thefloorIsShipped() {
        for (String file : FLOOR) {
            assertThat(MESSAGES.resolve(file)).exists();
        }
    }

    @Test
    @DisplayName("every language file answers every key the code names")
    void everyfileAnswersEveryKey() throws IOException, ConfigurateException {
        List<String> missing = new ArrayList<>();
        for (Path file : catalogues()) {
            Set<String> paths = pathsIn(file);
            for (Keys key : Keys.values()) {
                if (!paths.contains(key.path())) {
                    missing.add(file.getFileName() + " has no " + key.path());
                }
            }
        }

        assertThat(missing)
                .describedAs("A key a file does not answer falls back to English, silently, forever.")
                .isEmpty();
    }

    @Test
    @DisplayName("no language file carries a key the code does not know")
    void nofileCarriesADeadKey() throws IOException, ConfigurateException {
        Set<String> known = everyKeyAnybodyReads();
        Set<String> blocks = blocksNamedByAWindow();
        Set<String> prefixes = prefixesBuiltInTheCode();

        List<String> dead = new ArrayList<>();
        for (Path file : catalogues()) {
            for (String path : pathsIn(file)) {
                if (known.contains(path) || under(path, blocks) || under(path, prefixes)) {
                    continue;
                }
                dead.add(file.getFileName() + " carries " + path + ", which the code does not know");
            }
        }

        assertThat(dead)
                .describedAs("A dead key is a line a translator keeps translating for nothing.")
                .isEmpty();
    }

    @Test
    @DisplayName("the folder and the enum are both read, so this cannot pass by finding nothing")
    void bothsidesAreRead() throws IOException {
        assertThat(catalogues())
                .describedAs("this plugin ships language files, so a scan that finds none is a broken scan")
                .hasSizeGreaterThan(1);
        assertThat(Keys.values())
                .describedAs("this plugin has words, so an enum with nothing in it is a broken read")
                .isNotEmpty();
    }

    /**
     * Every key a line in one of these files can answer: this plugin's, and the library's own two sets.
     *
     * <p>The command layer and the menu engine both draw words nobody wrote a class for here: a help page,
     * a bad argument, a confirm prompt, a page arrow. uxmLib carries defaults for them and reads a plugin's
     * file on top, key by key, so a line under {@code command.} or {@code gui.} in one of these files is an
     * override and not a dead key. It is read off the library rather than listed, so a key uxmLib adds
     * tomorrow is known on the day it is added.
     */
    private static Set<String> everyKeyAnybodyReads() {
        Set<String> known = new LinkedHashSet<>();
        for (Keys key : Keys.values()) {
            known.add(key.path());
        }
        for (com.uxplima.uxmlib.command.annotation.CommandLine line :
                com.uxplima.uxmlib.command.annotation.CommandLine.values()) {
            known.add(line.path());
        }
        for (java.util.Map<String, String> words :
                com.uxplima.uxmlib.text.language.LibraryWords.shipped().values()) {
            known.addAll(words.keySet());
        }
        return known;
    }

    /**
     * Every block a shipped window names with an at sign.
     *
     * <p>A window file is where the other half of the words live. A line reads {@code name =
     * "@menu.button.next"} or {@code lore = ["tile:5 @menu.wiki.entry type maximum"]}, and the engine looks
     * the block up by path: the title, the crumb, each fact's label and value. No constant of ours is named
     * for any of them, and the block is written once, in the file.
     */
    private static Set<String> blocksNamedByAWindow() throws IOException {
        Set<String> blocks = new LinkedHashSet<>();
        // A window file writes the at sign, and so does the code that hands the engine a value: a tile's
        // fact is "@menu.storage.collecting" in Java exactly as it is in a file. Both are a reference to a
        // path in the catalogue, so both are read here.
        Matcher named = Pattern.compile("@([a-z0-9._%-]+)").matcher(everyShippedFile() + everyJavaFile());
        while (named.find()) {
            String block = named.group(1);
            int token = block.indexOf('%');
            String path = token < 0 ? block : block.substring(0, token);
            blocks.add(path);
            // The block a named path sits in, so its siblings count too. A requirement block is written
            // once in the words and named a line at a time by a content file: a level that asks for money
            // names "@minion.upgrade.money", and the "permission" beside it is the line the next level
            // names when it asks for a node instead. It is shipped vocabulary and not a dead key.
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                blocks.add(path.substring(0, dot));
            }
        }
        if (everyShippedFile().contains("tile:")) {
            // The two words the engine writes over a tooltip's blocks. MenuTiles reads them by path, so no
            // constant of ours is ever named. It is allowed only while a shipped window really draws a
            // tile: the day none does, the words go with them and this stops excusing anything.
            blocks.add("menu.lore.");
        }
        return blocks;
    }

    /**
     * Every path the code builds rather than writes, read as the part before the number.
     *
     * <p>A lore of eight lines is eight keys and one line of Java: {@code MessageKey.of("item.bow-lore-" +
     * line, ...)}. The enum names the first of them at most, so the rest are only ever reached through the
     * literal that builds them. A literal that ends in a dot or a hyphen is that shape and nothing else is.
     */
    private static Set<String> prefixesBuiltInTheCode() throws IOException {
        Set<String> prefixes = new LinkedHashSet<>();
        // The enum is read here and nowhere else in this test: a key built out of a literal is often built
        // in the enum itself, beside the constants that name the first one or two of its lines.
        Matcher literal = Pattern.compile("\"([a-z][a-z0-9._-]*[.-])\"").matcher(everyJavaFile());
        while (literal.find()) {
            prefixes.add(literal.group(1));
        }
        return prefixes;
    }

    /** Whether a path sits under one of those, which is what makes it a key something reads. */
    private static boolean under(String path, Set<String> owners) {
        for (String owner : owners) {
            if (path.equals(owner)
                    || path.startsWith(owner.endsWith(".") ? owner : owner + ".")
                    || (owner.endsWith("-") && path.startsWith(owner))) {
                return true;
            }
        }
        return false;
    }

    /** Every line of Java this plugin holds, the key enum included. */
    private static String everyJavaFile() throws IOException {
        return readAll(Path.of("src", "main", "java"), name -> name.endsWith(".java"));
    }

    /** Every file this plugin ships, which is where a window names a block with an at sign. */
    private static String everyShippedFile() throws IOException {
        return readAll(Path.of("src", "main", "resources"), name -> name.endsWith(".conf"));
    }

    private static String readAll(Path root, java.util.function.Predicate<String> wanted) throws IOException {
        StringBuilder joined = new StringBuilder();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (wanted.test(file.getFileName().toString())) {
                    joined.append(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
                }
            }
        }
        return joined.toString();
    }

    private static List<Path> catalogues() throws IOException {
        try (Stream<Path> files = Files.list(MESSAGES)) {
            return files.filter(file -> file.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        }
    }

    /** Every leaf path in a file, written the way a key writes it. */
    private static Set<String> pathsIn(Path file) throws ConfigurateException {
        ConfigurationNode root =
                HoconConfigurationLoader.builder().path(file).build().load();
        Set<String> found = new LinkedHashSet<>();
        collect(root, "", found);
        return found;
    }

    private static void collect(ConfigurationNode node, String prefix, Set<String> into) {
        if (node.isMap()) {
            node.childrenMap().forEach((key, child) -> {
                String path = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
                collect(child, path, into);
            });
            return;
        }
        if (!prefix.isEmpty()) {
            into.add(prefix);
        }
    }
}
