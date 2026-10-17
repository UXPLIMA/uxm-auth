package com.uxplima.auth.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every line a shipped screen names from the catalogue is answered by every language.
 *
 * <p>A screen draws a key no language has as the key itself: a player at the door reads
 * {@code screen.login.title} where the server's name should be, on the first thing they see.
 */
final class EveryScreenLineIsAnsweredTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    @DisplayName("every @key in screens.conf is a line of every language")
    void everyScreenKeyIsAnswered() throws IOException, ConfigurateException {
        Set<String> named = new LinkedHashSet<>();
        collectKeys(load(RESOURCES.resolve("screens.conf")), named);

        List<String> missing = new ArrayList<>();
        for (Path catalogue : catalogues()) {
            Set<String> answered = new LinkedHashSet<>();
            collectPaths(load(catalogue), "", answered);
            for (String key : named) {
                if (!answered.contains(key)) {
                    missing.add(catalogue.getFileName() + " has no " + key);
                }
            }
        }

        assertThat(named)
                .describedAs("screens.conf names its words from the catalogue")
                .isNotEmpty();
        assertThat(missing).isEmpty();
    }

    private static void collectKeys(ConfigurationNode node, Set<String> into) {
        if (node.isMap()) {
            node.childrenMap().values().forEach(child -> collectKeys(child, into));
            return;
        }
        if (node.isList()) {
            node.childrenList().forEach(child -> collectKeys(child, into));
            return;
        }
        if (node.raw() instanceof String text && text.startsWith("@")) {
            into.add(text.substring(1));
        }
    }

    private static void collectPaths(ConfigurationNode node, String prefix, Set<String> into) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            collectPaths(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, into));
            return;
        }
        if (!prefix.isEmpty()) {
            into.add(prefix);
        }
    }

    private static ConfigurationNode load(Path file) throws ConfigurateException {
        return HoconConfigurationLoader.builder().path(file).build().load();
    }

    private static List<Path> catalogues() throws IOException {
        try (Stream<Path> files = Files.list(RESOURCES.resolve("messages"))) {
            return files.filter(path -> path.toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        }
    }
}
