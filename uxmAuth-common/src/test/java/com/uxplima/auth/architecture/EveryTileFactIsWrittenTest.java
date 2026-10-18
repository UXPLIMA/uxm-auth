package com.uxplima.auth.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Every line a shipped tile draws is a line the catalogue holds, read the way the library reads a tile.
 *
 * <p>A tile is written {@code tile:<colour> @<key> [fact ...]}. The library draws {@code <key>.title} on
 * every tile, and for each fact it draws two lines, {@code <key>.<fact>.label} and
 * {@code <key>.<fact>.value}. A line it draws and cannot find is drawn as its own path, so a fact with no
 * label puts {@code menu.house.tile.seller.label} on the tile in front of a player.
 *
 * <p>{@code EveryWindowKeyIsWrittenTest} cannot see this. It lets a name cover every key beneath it, which
 * is right for a block and blind for a fact: a block with a title and nothing else answers for any number
 * of facts nobody wrote. uxm-plots shipped 172 such rows per language on 2026-09-22 with that guard green,
 * because every guard it had read the grammar its files assumed rather than the one the library draws.
 *
 * <p>The crumb, the description and the closing sentence are drawn only when they exist, so they are not
 * asked for. A word with a minus leaves a block out, {@code action:} names a sentence the library also
 * leaves off when it is missing, and {@code state:<fact>} is a fact like any other. A key or a fact with a
 * placeholder in it is decided when it is drawn, and it is skipped.
 */
final class EveryTileFactIsWrittenTest {

    private static final Path MENUS = Path.of("src", "main", "resources", "menus");
    private static final Path CATALOGUE = Path.of("src", "main", "resources", "messages", "messages_en.conf");

    private static final String MARK = "tile:";
    private static final Pattern SPACE = Pattern.compile("\\s+");
    private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");
    private static final Pattern KEY = Pattern.compile("[a-z0-9][a-z0-9._-]*");
    private static final Pattern FACT = Pattern.compile("[a-z0-9][a-z0-9_-]*");

    @Test
    @DisplayName("every title and every fact a shipped tile draws is written in the catalogue")
    void everyTileLineIsWritten() throws ConfigurateException {
        Set<String> written = catalogueKeys();
        List<String> missing = new ArrayList<>();

        for (String line : linesEveryTileDraws()) {
            if (!written.contains(line)) {
                missing.add(line);
            }
        }

        assertThat(missing)
                .describedAs("the library draws each of these on a tile and none is written, so the player reads"
                        + " the path itself. A fact needs <key>.<fact>.label and <key>.<fact>.value, and every"
                        + " tile needs <key>.title")
                .isEmpty();
    }

    @Test
    @DisplayName("the grammar is read the library's way, so this cannot pass by reading nothing")
    void theGrammarIsReadTheLibrarysWay() {
        assertThat(drawnBy("tile:5 @menu.house.tile seller -action state:left:good action:@menu.buy %x%"))
                .containsExactly(
                        "menu.house.tile.title",
                        "menu.house.tile.seller.label",
                        "menu.house.tile.seller.value",
                        "menu.house.tile.left.label",
                        "menu.house.tile.left.value");
        assertThat(drawnBy("tile:<colour> @<key> [fact ...]")).isEmpty();
        assertThat(drawnBy("tile:%colour% @menu.%state%")).isEmpty();
    }

    /** Every catalogue line the shipped tiles draw, comments included: a commented tile is one an operator uncomments. */
    private static Set<String> linesEveryTileDraws() {
        Set<String> drawn = new LinkedHashSet<>();
        for (Path file : menus()) {
            Matcher value = QUOTED.matcher(read(file));
            while (value.find()) {
                drawn.addAll(drawnBy(value.group(1)));
            }
        }
        return drawn;
    }

    /** The lines one written tile draws, in the order the library draws them. */
    private static List<String> drawnBy(String written) {
        String line = written.strip();
        if (!line.startsWith(MARK)) {
            return List.of();
        }
        String[] words = SPACE.split(line, -1);
        List<String> drawn = new ArrayList<>();
        String key = null;
        for (int at = 1; at < words.length; at++) {
            String word = words[at];
            if (key == null) {
                if (!word.startsWith("@")) {
                    continue;
                }
                key = word.substring(1);
                if (!KEY.matcher(key).matches()) {
                    return List.of();
                }
                drawn.add(key + ".title");
                continue;
            }
            String fact = factNamedBy(word);
            if (fact != null) {
                drawn.add(key + "." + fact + ".label");
                drawn.add(key + "." + fact + ".value");
            }
        }
        return drawn;
    }

    /** The fact a word names, or nothing when the word leaves a block out, names a sentence or waits on a value. */
    private static @Nullable String factNamedBy(String word) {
        if (word.startsWith("-") || word.startsWith("action:")) {
            return null;
        }
        String name = word.startsWith("state:") ? word.split(":", -1)[1] : word;
        return FACT.matcher(name).matches() ? name : null;
    }

    private static Set<String> catalogueKeys() throws ConfigurateException {
        Set<String> keys = new LinkedHashSet<>();
        collect(HoconConfigurationLoader.builder().path(CATALOGUE).build().load(), "", keys);
        return keys;
    }

    private static void collect(ConfigurationNode node, String prefix, Set<String> into) {
        if (node.isMap()) {
            node.childrenMap()
                    .forEach((name, child) ->
                            collect(child, prefix.isEmpty() ? name.toString() : prefix + "." + name, into));
            return;
        }
        if (!prefix.isEmpty()) {
            into.add(prefix);
        }
    }

    private static List<Path> menus() {
        if (!Files.isDirectory(MENUS)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(MENUS)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + MENUS, e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
    }
}
