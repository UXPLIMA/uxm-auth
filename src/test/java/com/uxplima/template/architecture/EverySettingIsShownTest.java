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

import com.uxplima.template.testing.SourcePaths;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every setting the reader reads is one the shipped {@code config.conf} shows.
 *
 * <p>A key the code reads and the file never mentions is a setting an operator cannot find. It works
 * perfectly for whoever knows the path, the default is what everybody else gets, and nothing anywhere says
 * the choice exists. uxmJobs had one: {@code boosters.combine} decided how two boosters are put together,
 * the shipped file never named it, and the key an operator could find, in {@code boosters.conf} under
 * fifteen lines explaining the two answers, was read by nothing.
 *
 * <p>Only {@code PluginSettings} is read, because it is the one class that reads {@code config.conf}. A
 * path ending in a dot is a prefix an operator fills with names of their own and is not a key.
 */
final class EverySettingIsShownTest {

    private static final Path READER =
            SourcePaths.of(EverySettingIsShownTest.class, "adapter", "outbound", "PluginSettings.java");

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** {@code config.getInt("a.b", 1)} and every other typed read, by the path it names. */
    private static final Pattern READ =
            Pattern.compile("\\.get(?:Int|Long|Double|Boolean|String|List)\\(\\s*\"([a-z0-9][a-z0-9.\\-]*)\"");

    /**
     * Every string in the reader that is shaped like a path.
     *
     * <p>The other direction asks what the reader reads, and for that a typed read is the whole answer.
     * This one asks whether a key in the file reaches the code at all, and a key reaches it in more ways
     * than a typed read: {@code keys("rerolls.groups")} walks every group an operator wrote,
     * {@code nodeAt("link-tool", "item")} hands a block to the library's item reader, and a ladder is
     * read by a helper that takes the block as an argument and builds the leaf paths itself.
     *
     * <p>So the reader is asked for every literal shaped like a path rather than for the ones inside a
     * call this guard knows. It is one class and it reads one file, so a path shaped literal in it is a
     * path: a value that happens to look like one only makes this guard more forgiving, and what it is
     * looking for is a key nothing in the class mentions at all.
     */
    private static final Pattern PATH_SHAPED = Pattern.compile("\"([a-z0-9][a-z0-9.\\-]*)\"");

    @Test
    @DisplayName("every setting the reader reads is shown in a file this plugin ships")
    void everySettingIsShipped() throws IOException, ConfigurateException {
        List<ConfigurationNode> shipped = shippedFiles();
        List<String> hidden = new ArrayList<>();

        for (String path : settingsRead()) {
            if (path.endsWith(".") || shownIn(shipped, path)) {
                continue;
            }
            hidden.add(path);
        }

        assertThat(hidden)
                .describedAs("A setting no shipped file shows is one an operator cannot find. It works for"
                        + " whoever knows the path and everybody else gets the default.")
                .isEmpty();
    }

    /**
     * Whether any shipped file shows this path.
     *
     * <p>Every shipped file and not only {@code config.conf}, because the reader reads more than one and
     * a setting is findable wherever it is written down. uxmSkills moved its booster mode into
     * {@code boosts.conf} beside the table it governs, which is the right place for it and is not
     * {@code config.conf}.
     */
    private static boolean shownIn(List<ConfigurationNode> shipped, String path) {
        Object[] parts = path.split("\\.", -1);
        for (ConfigurationNode file : shipped) {
            if (!file.node(parts).virtual()) {
                return true;
            }
        }
        return false;
    }

    /** Every {@code .conf} this plugin ships at the top of its data folder. */
    private static List<ConfigurationNode> shippedFiles() throws IOException, ConfigurateException {
        List<ConfigurationNode> roots = new ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.list(RESOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".conf"))
                    .sorted()
                    .toList()) {
                roots.add(HoconConfigurationLoader.builder().path(file).build().load());
            }
        }
        return roots;
    }

    @Test
    @DisplayName("every key the shipped config.conf writes is read by something")
    void everyShippedKeyIsRead() throws IOException, ConfigurateException {
        Set<String> read = pathsNamed();
        List<String> dead = new ArrayList<>();

        for (String path : leavesOf(configFile())) {
            if (isRead(read, path)) {
                continue;
            }
            dead.add(path);
        }

        assertThat(dead)
                .describedAs("A key the shipped file documents and nothing reads is a promise the code does"
                        + " not keep: an operator writes it, nothing happens, and there is no way to tell"
                        + " that from a value that did not take effect.")
                .isEmpty();
    }

    /**
     * Whether this key, or a block it sits inside, is read.
     *
     * <p>A prefix counts because a block is often read whole: an item is handed to the library's item
     * reader, a per language name is read by locale, and a block an operator fills with names of their own
     * is walked rather than named. What is not allowed is a leaf nobody reads under a block nobody reads.
     */
    private static boolean isRead(Set<String> read, String path) {
        if (LIBRARY_OWNED.stream().anyMatch(path::startsWith)) {
            return true;
        }
        String walked = path;
        while (!walked.isEmpty()) {
            if (read.contains(walked) || read.contains(walked + ".")) {
                return true;
            }
            int dot = walked.lastIndexOf('.');
            walked = dot < 0 ? "" : walked.substring(0, dot);
        }
        return false;
    }

    /**
     * The blocks a library reads rather than this plugin, named one at a time with the reason.
     *
     * <p>Each of these is a key the plugin hands over whole: the claim seam validates its own providers and
     * warns about a name nobody registered, and the language block is the same three keys in every plugin
     * of ours and is read by the resolver. Reading them into a record here would be a second spelling of
     * the same thing.
     */
    private static final Set<String> LIBRARY_OWNED = Set.of("claims.", "language.");

    /** Every leaf of a tree, as the dotted path an operator would write. */
    private static List<String> leavesOf(ConfigurationNode node) {
        List<String> leaves = new ArrayList<>();
        if (node.isMap()) {
            for (ConfigurationNode child : node.childrenMap().values()) {
                leaves.addAll(leavesOf(child));
            }
            return leaves;
        }
        Object[] path = node.path().array();
        StringBuilder written = new StringBuilder();
        for (Object part : path) {
            if (written.length() > 0) {
                written.append('.');
            }
            written.append(part);
        }
        leaves.add(written.toString());
        return leaves;
    }

    /** The one file this guard reads in the other direction: what an operator is handed on first run. */
    private static ConfigurationNode configFile() throws ConfigurateException {
        return HoconConfigurationLoader.builder()
                .path(RESOURCES.resolve("config.conf"))
                .build()
                .load();
    }

    @Test
    @DisplayName("the shipped file is read, so this guard cannot pass by finding no keys")
    void theshippedFileIsRead() throws ConfigurateException {
        assertThat(leavesOf(configFile()))
                .describedAs("A file that parsed to nothing is a guard that always passes.")
                .isNotEmpty();
    }

    @Test
    @DisplayName("the reader is read, so this guard cannot pass by finding no settings")
    void thereaderIsRead() throws IOException {
        assertThat(settingsRead())
                .describedAs("A pattern that matches nothing is a guard that always passes.")
                .isNotEmpty();
    }

    /** Every path the reader names in a typed read, which is what the other direction asks about. */
    private static Set<String> settingsRead() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        Matcher read = READ.matcher(Files.readString(READER, StandardCharsets.UTF_8));
        while (read.find()) {
            paths.add(read.group(1));
        }
        return paths;
    }

    /**
     * Every path shaped literal in this plugin's own Java.
     *
     * <p>The other direction asks the one class that reads {@code config.conf}, because that is where a
     * setting is read. This one asks whether a key an operator can write reaches the code at all, and in
     * some plugins it reaches a second reader: uxmAuction hands whole blocks of its file to classes in
     * {@code adapter/outbound/config}, so asking only {@code PluginSettings} read thirty of its live
     * settings as dead. A key is findable wherever the code names it.
     */
    private static Set<String> pathsNamed() throws IOException {
        Set<String> paths = new LinkedHashSet<>();
        try (java.util.stream.Stream<Path> files = Files.walk(Path.of("src", "main", "java"))) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher named = PATH_SHAPED.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (named.find()) {
                    paths.add(named.group(1));
                }
            }
        }
        return paths;
    }
}
