package com.uxplima.auth.paper.architecture;

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

import com.uxplima.auth.paper.testing.Modules;
import com.uxplima.uxmlib.condition.action.ActionParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every line of every shipped effect list is one the action engine can read, and every key one of them names
 * is answered by every language.
 *
 * <p>Both mistakes are quiet. A verb the engine does not know drops the whole list with a warning nobody
 * reads, so a player who registers sees nothing happen. A key no language has reaches the player as its own
 * dotted path. A shipped file that is wrong is wrong on every server that installs the plugin.
 *
 * <p>This lives in the Paper module because the engine builds its actions against the server's classes.
 * The file it reads is the common module's, which is the one every platform ships.
 */
final class EveryEffectLineIsReadableTest {

    /** A line: its verb, any modifier block after it, and its payload. */
    private static final Pattern LINE = Pattern.compile("^\\[([a-z-]+)\\](?:\\s*\\{[^}]*\\})?\\s*(.*)$");

    /** The times after a subtitle's second part: up to three numbers at the end. */
    private static final Pattern TIMES = Pattern.compile("(\\s+[0-9.]+){1,3}\\s*$");

    /** A text part that names its words by key, and nothing else. */
    private static final Pattern KEY = Pattern.compile("^@([A-Za-z0-9._-]+)$");

    @Test
    @DisplayName("every shipped effect line is one the action engine can read")
    void everyLineIsReadable() throws ConfigurateException {
        List<String> unreadable = new ArrayList<>();
        for (String line : shippedEffectLines()) {
            try {
                ActionParser.parse(line);
            } catch (IllegalArgumentException refused) {
                unreadable.add(line + " (" + refused.getMessage() + ")");
            }
        }

        assertThat(unreadable)
                .describedAs("A line the engine cannot read does nothing, and does it quietly.")
                .isEmpty();
    }

    @Test
    @DisplayName("every key a shipped effect line names is answered by every language")
    void everyKeyIsAnswered() throws IOException, ConfigurateException {
        Set<String> named = new LinkedHashSet<>();
        for (String line : shippedEffectLines()) {
            named.addAll(keysIn(line));
        }

        List<String> missing = new ArrayList<>();
        for (Path file : catalogues()) {
            Set<String> answered = pathsIn(file);
            for (String path : named) {
                if (!answered.contains(path)) {
                    missing.add(file.getFileName() + " has no " + path);
                }
            }
        }

        assertThat(missing)
                .describedAs("A key no language answers reaches the player as its own path.")
                .isEmpty();
    }

    @Test
    @DisplayName("a key is read from every text part, the way the engine reads it")
    void everyTextPartIsRead() {
        assertThat(keysIn("[subtitle] @a.title | @a.subtitle 0.1 1.2 0.3")).containsExactly("a.title", "a.subtitle");
        assertThat(keysIn("[bossbar] 5 RED PROGRESS | @a.bar")).containsExactly("a.bar");
        assertThat(keysIn("[message] {delay=40} @a.chat")).containsExactly("a.chat");
        assertThat(keysIn("[message] mail me @ the forum")).isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothSidesAreRead() throws IOException, ConfigurateException {
        assertThat(shippedEffectLines())
                .describedAs("this plugin ships effect lists, so a scan that finds none is a broken scan")
                .hasSizeGreaterThan(5);
        assertThat(catalogues())
                .describedAs("this plugin ships language files, so a scan that finds none is a broken scan")
                .hasSizeGreaterThan(1);
    }

    private static List<String> keysIn(String line) {
        Matcher read = LINE.matcher(line.strip());
        if (!read.matches()) {
            return List.of();
        }
        String verb = read.group(1);
        String payload = read.group(2);
        int pipe = payload.indexOf('|');
        List<String> parts = new ArrayList<>();
        if (pipe >= 0 && verb.equals("subtitle")) {
            parts.add(payload.substring(0, pipe));
            parts.add(TIMES.matcher(payload.substring(pipe + 1)).replaceFirst(""));
        } else if (pipe >= 0 && verb.equals("bossbar")) {
            parts.add(payload.substring(pipe + 1));
        } else {
            parts.add(payload);
        }
        List<String> keys = new ArrayList<>();
        for (String part : parts) {
            Matcher key = KEY.matcher(part.strip());
            if (key.matches()) {
                keys.add(key.group(1));
            }
        }
        return keys;
    }

    private static List<String> shippedEffectLines() throws ConfigurateException {
        ConfigurationNode effects = HoconConfigurationLoader.builder()
                .path(Modules.commonResources().resolve("config.conf"))
                .build()
                .load()
                .node("effects");
        List<String> lines = new ArrayList<>();
        for (ConfigurationNode moment : effects.childrenMap().values()) {
            lines.addAll(moment.getList(String.class, List.of()));
        }
        return lines;
    }

    private static Set<String> pathsIn(Path file) throws ConfigurateException {
        Set<String> paths = new LinkedHashSet<>();
        collect(HoconConfigurationLoader.builder().path(file).build().load(), "", paths);
        return paths;
    }

    private static void collect(ConfigurationNode node, String prefix, Set<String> paths) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            collect(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, paths));
            return;
        }
        if (!prefix.isEmpty()) {
            paths.add(prefix);
        }
    }

    private static List<Path> catalogues() throws IOException {
        try (Stream<Path> files = Files.list(Modules.commonResources().resolve("messages"))) {
            return files.filter(path -> path.getFileName().toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        }
    }
}
