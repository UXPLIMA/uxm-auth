package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every language names each label of a line one way, in its own words and in capitals.
 *
 * <p>A label is the word before the arrow of a line: {@code <tag:'...'>} for the plugin, {@code <etag:'...'>} for a
 * failure. On 2026-09-23 uxm-plots found the Turkish files of the family mixed: one file wrote the error label
 * {@code ERROR}, {@code Hata} and {@code HATA} on different lines, and one still carried the template's own label.
 * The English labels are capitals, drawn in small capitals; Turkish has no small capitals, so its labels are written
 * in capitals to read the same way. A label is a word a translator moves, so a language may not borrow the English
 * error word.
 */
final class EveryLabelIsTranslatedOneWayTest {

    private static final Path MESSAGES = Path.of("src", "main", "resources", "messages");

    private static final Pattern LABEL = Pattern.compile("<(e?tag):'([^']*)'>");

    private static final Set<String> ENGLISH_ONLY = Set.of("ERROR", "WARNING");

    @Test
    @DisplayName("each English label is written one way in every other language, in capitals, and never borrowed")
    void everyLabelIsTranslatedOneWay() throws IOException {
        Map<String, String> english = catalogue(MESSAGES.resolve("messages_en.conf"));
        List<String> wrong = new ArrayList<>();
        int compared = 0;
        for (Path file : languages()) {
            Locale language = Locale.forLanguageTag(
                    file.getFileName().toString().replace("messages_", "").replace(".conf", ""));
            Map<String, Set<String>> ways = new TreeMap<>();
            for (Map.Entry<String, String> line : catalogue(file).entrySet()) {
                List<String> theirs = labels(line.getValue());
                List<String> ours = labels(english.getOrDefault(line.getKey(), ""));
                for (String label : theirs) {
                    String bare = label.substring(label.indexOf(':') + 1);
                    if (ENGLISH_ONLY.contains(bare)) {
                        wrong.add(file.getFileName() + " borrows " + bare + " at " + line.getKey());
                    }
                    if (!bare.equals(bare.toUpperCase(language))) {
                        wrong.add(file.getFileName() + " writes " + bare + " in lower case at " + line.getKey());
                    }
                }
                if (theirs.size() == ours.size()) {
                    for (int i = 0; i < ours.size(); i++) {
                        ways.computeIfAbsent(ours.get(i), key -> new LinkedHashSet<>())
                                .add(theirs.get(i));
                        compared++;
                    }
                }
            }
            ways.forEach((label, written) -> {
                if (written.size() > 1) {
                    wrong.add(file.getFileName() + " writes " + label + " as " + written);
                }
            });
        }

        assertThat(compared)
                .describedAs("a scan that paired no label read nothing")
                .isPositive();
        assertThat(wrong).isEmpty();
    }

    private static List<Path> languages() throws IOException {
        try (Stream<Path> files = Files.list(MESSAGES)) {
            return files.filter(path -> path.getFileName().toString().startsWith("messages_"))
                    .filter(path -> !path.getFileName().toString().equals("messages_en.conf"))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> labels(String line) {
        List<String> found = new ArrayList<>();
        Matcher matcher = LABEL.matcher(line);
        while (matcher.find()) {
            found.add(matcher.group(1) + ":" + matcher.group(2));
        }
        return found;
    }

    private static Map<String, String> catalogue(Path file) throws IOException {
        ConfigurationNode root =
                HoconConfigurationLoader.builder().path(file).build().load();
        Map<String, String> lines = new LinkedHashMap<>();
        flatten(root, "", lines);
        return lines;
    }

    private static void flatten(ConfigurationNode node, String prefix, Map<String, String> into) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            flatten(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, into));
            return;
        }
        String line = node.getString();
        if (line != null) {
            into.put(prefix, line);
        }
    }
}
