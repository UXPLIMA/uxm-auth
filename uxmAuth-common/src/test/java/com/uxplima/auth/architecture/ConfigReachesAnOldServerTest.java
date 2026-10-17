package com.uxplima.auth.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.NodePath;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The promise the shipped configuration makes to a server that was installed a year ago.
 *
 * <p>A plugin that writes its file on the first run and never again gives every later key to a new server
 * and to no existing one: the operator who has run it longest is the one who never gets the knob, and they
 * find out by reading a changelog. Every plugin of this family closes that the same way, through
 * {@code PluginSettings.bringUpToDate}, and this is the guard that says so.
 *
 * <p>It asks two different things. Four tests drive the merge against the file this jar really ships, so a
 * resource that moved or a merge that stopped working is caught here rather than on somebody's server. The
 * fifth reads the boot and fails a plugin that has the method and never calls it, which is the way this
 * would rot: the mechanism is one line in a composition root and nothing else would miss it.
 */
final class ConfigReachesAnOldServerTest {

    /** The file every plugin of ours ships, and the one the merge is about. */
    private static final String SHIPPED = "config.conf";

    /** A value no shipped file holds, so a merge that overwrote it would be unmistakable. */
    private static final String WRITTEN_BY_HAND = "uxm-guard-written-by-hand";

    @Test
    @DisplayName("a file written before this release gains the keys the release adds")
    void anOldFileGainsWhatThisReleaseAdds(@TempDir Path folder) throws IOException {
        Path file = aFileFromAnOlderRelease(folder);

        assertThat(PluginSettings.bringUpToDate(file, resources())).isTrue();

        assertThat(keysOf(read(file)))
                .describedAs("every block the shipped file has is in the operator's file now")
                .containsAll(keysOf(shipped()));
    }

    @Test
    @DisplayName("no value the operator wrote is changed")
    void anOperatorValueSurvives(@TempDir Path folder) throws IOException {
        Path file = aFileFromAnOlderRelease(folder);
        NodePath theirs = aWrittenLeaf();

        PluginSettings.bringUpToDate(file, resources());

        assertThat(read(file).node(theirs).getString())
                .describedAs("the operator wrote %s and the shipped file says something else", theirs)
                .isEqualTo(WRITTEN_BY_HAND);
    }

    @Test
    @DisplayName("the file as it was is kept beside the new one")
    void theOldFileIsKept(@TempDir Path folder) throws IOException {
        Path file = aFileFromAnOlderRelease(folder);

        PluginSettings.bringUpToDate(file, resources());

        Path copy = folder.resolve(SHIPPED + ".bak");
        assertThat(copy).exists();
        assertThat(Files.readString(copy, StandardCharsets.UTF_8)).contains(WRITTEN_BY_HAND);
    }

    @Test
    @DisplayName("a file that already has everything is not rewritten, and no copy is made")
    void aCurrentFileIsLeftAlone(@TempDir Path folder) throws IOException {
        Path file = folder.resolve(SHIPPED);
        try (InputStream open = shippedStream()) {
            Files.copy(open, file);
        }
        String before = Files.readString(file, StandardCharsets.UTF_8);

        assertThat(PluginSettings.bringUpToDate(file, resources())).isFalse();

        assertThat(Files.readString(file, StandardCharsets.UTF_8))
                .describedAs("a start that adds nothing must not move an operator's formatting")
                .isEqualTo(before);
        assertThat(folder.resolve(SHIPPED + ".bak")).doesNotExist();
    }

    @Test
    @DisplayName("a second start adds nothing, so the file is not rewritten on every boot")
    void theSecondStartIsQuiet(@TempDir Path folder) throws IOException {
        Path file = aFileFromAnOlderRelease(folder);
        assertThat(PluginSettings.bringUpToDate(file, resources())).isTrue();
        String after = Files.readString(file, StandardCharsets.UTF_8);

        assertThat(PluginSettings.bringUpToDate(file, resources()))
                .describedAs("the merge is idempotent: what it added the first time is there the second")
                .isFalse();
        assertThat(Files.readString(file, StandardCharsets.UTF_8)).isEqualTo(after);
    }

    @Test
    @DisplayName("a server with no file yet is left to the first run, which writes the whole shipped file")
    void anAbsentFileIsNotTouched(@TempDir Path folder) {
        Path file = folder.resolve(SHIPPED);

        assertThat(PluginSettings.bringUpToDate(file, resources())).isFalse();
        assertThat(file).doesNotExist();
    }

    @Test
    @DisplayName("the boot brings the file up to date, so the merge is not a method nobody calls")
    void theBootBringsTheFileUpToDate() throws IOException {
        List<Path> callers = new ArrayList<>();
        for (Path source : bootSources()) {
            if (Files.readString(source, StandardCharsets.UTF_8).contains("bringUpToDate(")) {
                callers.add(source);
            }
        }

        assertThat(callers)
                .describedAs(
                        "a plugin whose boot never calls bringUpToDate gives its new keys to new" + " servers only")
                .isNotEmpty();
    }

    /**
     * A file holding one leaf the shipped file also has, written with a value nobody shipped.
     *
     * <p>It stands for the file of a server that was installed several releases ago: a few of today's keys,
     * their own values in them, and everything the releases since then added still absent.
     */
    private static Path aFileFromAnOlderRelease(Path folder) throws IOException {
        Path file = folder.resolve(SHIPPED);
        HoconConfigurationLoader loader =
                HoconConfigurationLoader.builder().path(file).build();
        try {
            CommentedConfigurationNode root = loader.createNode();
            root.node(aWrittenLeaf()).set(WRITTEN_BY_HAND);
            loader.save(root);
        } catch (ConfigurateException unwritable) {
            throw new IOException(unwritable);
        }
        return file;
    }

    /**
     * The first leaf of the shipped file that holds a word.
     *
     * <p>Which one it is does not matter: the assertion is that a value already in the file survives the
     * merge, and a word is the one shape that can be written over any shipped default without the loader
     * having an opinion about the type.
     */
    private static NodePath aWrittenLeaf() {
        NodePath found = firstWord(shipped());
        assertThat(found)
                .describedAs("the shipped config.conf holds no text value, so this guard has nothing to"
                        + " write over: give it one or change what it looks for")
                .isNotNull();
        return Objects.requireNonNull(found);
    }

    private static @org.jspecify.annotations.Nullable NodePath firstWord(ConfigurationNode node) {
        if (node.isMap()) {
            for (Map.Entry<Object, ? extends ConfigurationNode> child :
                    node.childrenMap().entrySet()) {
                NodePath found = firstWord(child.getValue());
                if (found != null) {
                    return found;
                }
            }
            return null;
        }
        return node.raw() instanceof String ? node.path() : null;
    }

    private static List<String> keysOf(ConfigurationNode node) {
        return node.childrenMap().keySet().stream().map(String::valueOf).toList();
    }

    private static ConfigurationNode read(Path file) throws IOException {
        try {
            return HoconConfigurationLoader.builder().path(file).build().load();
        } catch (ConfigurateException unreadable) {
            throw new IOException(unreadable);
        }
    }

    private static ConfigurationNode shipped() {
        try (Reader reader = new InputStreamReader(shippedStream(), StandardCharsets.UTF_8)) {
            return HoconConfigurationLoader.builder()
                    .source(() -> new java.io.BufferedReader(reader))
                    .build()
                    .load();
        } catch (IOException unreadable) {
            throw new IllegalStateException("cannot read the shipped " + SHIPPED, unreadable);
        }
    }

    private static InputStream shippedStream() {
        return Objects.requireNonNull(
                ConfigReachesAnOldServerTest.class.getResourceAsStream("/" + SHIPPED),
                SHIPPED + " is not on the classpath");
    }

    private static ClassLoader resources() {
        return ConfigReachesAnOldServerTest.class.getClassLoader();
    }

    /**
     * The composition root, and nothing else.
     *
     * <p>The whole tree would answer yes on the class that declares the method, which is the one place the
     * name is guaranteed to be. The boot is where the call has to be, so the boot is what is read.
     */
    private static List<Path> bootSources() throws IOException {
        // The boot is in a platform module, so every module is read.
        List<Path> boots = new java.util.ArrayList<>();
        for (Path root : Modules.mainSources()) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> path.toString().contains("bootstrap"))
                        .forEach(boots::add);
            }
        }
        return boots;
    }
}
