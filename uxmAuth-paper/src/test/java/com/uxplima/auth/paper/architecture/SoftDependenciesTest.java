package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every plugin this one looks for by name is declared, so the server loads it first.
 *
 * <p>A plugin looked for by name and not declared is a coin flip on the load order, and it loses in the
 * quietest way there is. uxmSkills found it with Bedrock: the detector is resolved once, at enable, so a
 * Floodgate that loads after us leaves Bedrock detection off for the whole run, every form falls back to
 * a chest window, and nothing anywhere says why.
 *
 * <p>Nothing here is a dependency. Every entry is {@code required: false} and this plugin runs alone.
 *
 * <p>The list is derived from the source rather than typed here, because a typed list is one somebody
 * forgets to add to. Two shapes are read: a plugin this code asks the plugin manager for by name, and a
 * library seam that asks on our behalf, which is the one that gets missed.
 */
final class SoftDependenciesTest {

    private static final Path MANIFEST = Path.of("src", "main", "resources", "paper-plugin.yml");

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** A name this code hands the plugin manager itself. */
    private static final Pattern ASKED_BY_NAME =
            Pattern.compile("(?:getPlugin|isPluginEnabled|getPluginManager\\(\\)\\.getPlugin)\\(\\s*\"([^\"]+)\"");

    /**
     * The library seams that look a plugin up for us, and what each one asks for.
     *
     * <p>This is where the defect lives. A plugin gains Bedrock by taking a detector, gains placeholders by
     * registering a provider, and gains an economy by naming a shipped bridge, and in none of those three
     * does the plugin's own code ever write the name of the plugin the library will look for.
     */
    private static final Map<String, List<String>> SEAMS = seams();

    private static Map<String, List<String>> seams() {
        Map<String, List<String>> asked = new LinkedHashMap<>();
        asked.put("BedrockDetector", List.of("floodgate", "Geyser-Spigot"));
        asked.put("BedrockScreen", List.of("floodgate", "Geyser-Spigot"));
        asked.put("PlaceholderProvider", List.of("PlaceholderAPI"));
        asked.put("Placeholders.register", List.of("PlaceholderAPI"));
        asked.put("Economies.vault()", List.of("Vault"));
        asked.put("Economies.vaultUnlocked(", List.of("VaultUnlocked"));
        asked.put("Economies.playerPoints()", List.of("PlayerPoints"));
        asked.put("Economies.ecoBits()", List.of("EcoBits"));
        return Map.copyOf(asked);
    }

    @Test
    @DisplayName("every plugin this one looks up by name is declared, so the server loads it first")
    void everyLookedUpPluginIsDeclared() throws IOException {
        String manifest = Files.readString(MANIFEST, StandardCharsets.UTF_8);
        List<String> missing = new ArrayList<>();
        for (String plugin : new TreeSet<>(lookedUp())) {
            if (!manifest.contains("\n    " + plugin + ":")) {
                missing.add(plugin);
            }
        }

        assertThat(missing)
                .describedAs("a plugin looked for by name and not declared is a coin flip on the load"
                        + " order, and it loses silently. Declare it under dependencies.server with"
                        + " load: BEFORE and required: false")
                .isEmpty();
    }

    @Test
    @DisplayName("not one declared plugin is required, because this plugin runs alone")
    void notOneDeclaredPluginIsRequired() throws IOException {
        String manifest = Files.readString(MANIFEST, StandardCharsets.UTF_8);

        assertThat(manifest).doesNotContain("required: true");
        assertThat(count(manifest, "required: false"))
                .describedAs("every declared plugin carries both lines: load: BEFORE and required: false")
                .isEqualTo(count(manifest, "load: BEFORE"));
    }

    @Test
    @DisplayName("the derivation reads both shapes, so the first test cannot pass by finding nothing")
    void theDerivationReadsBothShapes() {
        assertThat(namesIn("server.getPluginManager().getPlugin(\"CoreProtect\")"))
                .contains("CoreProtect");
        assertThat(namesIn("if (manager.isPluginEnabled(\"Lands\")) {")).contains("Lands");
        assertThat(namesIn("BedrockDetector.forServer(plugin.getServer())")).contains("floodgate", "Geyser-Spigot");
        assertThat(namesIn("new ShopPlaceholders() implements PlaceholderProvider"))
                .contains("PlaceholderAPI");
        assertThat(namesIn("nothing here looks anything up")).isEmpty();
    }

    /** Every plugin name this plugin's own source reaches for, one way or the other. */
    private static List<String> lookedUp() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path file : sources()) {
            names.addAll(namesIn(Files.readString(file, StandardCharsets.UTF_8)));
        }
        return names;
    }

    /** The plugin names one piece of text reaches for. */
    private static List<String> namesIn(String text) {
        List<String> names = new ArrayList<>();
        Matcher asked = ASKED_BY_NAME.matcher(text);
        while (asked.find()) {
            names.add(asked.group(1));
        }
        for (Map.Entry<String, List<String>> seam : SEAMS.entrySet()) {
            if (text.contains(seam.getKey())) {
                names.addAll(seam.getValue());
            }
        }
        return names;
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCE)) {
            return files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }

    private static int count(String text, String needle) {
        int found = 0;
        int at = text.indexOf(needle);
        while (at >= 0) {
            found++;
            at = text.indexOf(needle, at + needle.length());
        }
        return found;
    }
}
