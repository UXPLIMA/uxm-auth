package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.bukkit.Sound;

import com.uxplima.auth.paper.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every sound a shipped file names is a sound this server has.
 *
 * <p>A sound this version does not have is silence. Nothing throws, nothing is logged where anybody
 * looks, and a click that was meant to answer with a note answers with nothing: the operator hears a
 * menu that feels broken and has no way to tell a missing sound from a sound they cannot hear. A name is
 * also the easiest thing in a file to misspell, because both spellings are legal and neither is checked
 * where it is written.
 *
 * <p>Both spellings ship in this estate. `block.note_block.pling` is the key the server prints and
 * `BLOCK_NOTE_BLOCK_PLING` is the constant an operator copies out of a wiki, and uxmLib answers either.
 * The fold here runs the way uxmLib's does, forwards from the key to the constant: dots become
 * underscores and the whole thing is upper cased. Folding the other way would have to guess which
 * underscores were dots.
 *
 * <p>The names come out of the class rather than out of a list. Reading the fields of {@code Sound} is
 * offline work: it is the names that are read and never a value, so no registry and no server is needed
 * and the set is exactly what the platform this build compiles against has.
 */
final class EveryShippedSoundExistsTest {

    private static final Path RESOURCES = Modules.commonResources();

    /**
     * A sound named as the value of a key, as {@code sound = "block.chest.open"}.
     *
     * <p>The value has to carry a dot or an underscore, which every sound name does and no switch does:
     * {@code sound = false} is a channel being turned off and not a sound called false.
     */
    private static final Pattern SETTING =
            Pattern.compile("(?m)^\\s*[a-z-]*sounds?\\s*=\\s*\"?([A-Za-z0-9:]+[._][A-Za-z0-9_.:]*)");

    /**
     * Where a block of sounds starts: the word, then an opening brace or an equals and a bracket.
     *
     * <p>What follows is read by walking the brackets rather than by a pattern, because a block holds
     * any number of sounds and nests: uxmTeleportBow writes {@code sounds { shoot { key = "..." },
     * teleport { key = "..." } } } and uxmPlayerHider writes a list of blocks. A pattern that stopped at
     * the first closing brace read one sound of four and reported that by passing.
     */
    private static final Pattern BLOCK_START = Pattern.compile("sounds?\\s*=?\\s*[\\[{]");

    /** One {@code key = "..."} inside such a block. */
    private static final Pattern BLOCK_KEY = Pattern.compile("key\\s*=\\s*\"([A-Za-z0-9_.:]+)\"");

    /**
     * A sound named inside an action list, as {@code [sound] BLOCK_NOTE_BLOCK_PLING 0.6 1.5}.
     *
     * <p>The older {@code sound:NAME} spelling is read too. Both are in shipped files across the estate,
     * and a pattern that knew only one of them found nothing in a file written the other way and said so
     * by passing.
     *
     * <p>A brace block may sit between the verb and the name, as {@code [sound] {chance=0.2} NAME 1 1},
     * so it is skipped rather than read as a name.
     */
    private static final Pattern ACTION =
            Pattern.compile("(?:sound:|\\[sound\\]\\s*(?:\\{[^}]*\\}\\s*)?)([A-Za-z0-9_.:]+)");

    @Test
    @DisplayName("every sound a shipped file names is one the server has")
    void everyshippedSoundExists() throws IOException {
        Set<String> known = soundConstants();
        List<String> unknown = new ArrayList<>();

        for (Path file : shippedFiles()) {
            for (String written : soundsIn(file)) {
                if (!known.contains(constantOf(written))) {
                    unknown.add(file.getFileName() + ": " + written);
                }
            }
        }

        assertThat(unknown)
                .describedAs("A sound the server does not have is silence, and silence is what a click that"
                        + " was meant to answer sounds like.")
                .isEmpty();
    }

    @Test
    @DisplayName("both sides are read, so this cannot pass by finding nothing")
    void bothsidesAreRead() throws IOException {
        assertThat(soundConstants())
                .describedAs("the platform names its sounds in Sound, so an empty set is a broken read")
                .hasSizeGreaterThan(100);

        List<String> named = new ArrayList<>();
        for (Path file : shippedFiles()) {
            named.addAll(soundsIn(file));
        }
        assertThat(named)
                .describedAs("this plugin ships sounds, so a scan that finds none is a broken scan")
                .isNotEmpty();
    }

    /** The name the platform knows, folded from the key the way uxmLib folds it. */
    private static String constantOf(String written) {
        String name = written.strip();
        int namespace = name.indexOf(':');
        if (namespace >= 0) {
            name = name.substring(namespace + 1);
        }
        return name.replace('.', '_').toUpperCase(Locale.ROOT);
    }

    /** Every sound this file names, in either spelling. */
    private static List<String> soundsIn(Path file) throws IOException {
        String body = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
        List<String> found = new ArrayList<>();
        for (Pattern pattern : List.of(SETTING, ACTION)) {
            Matcher named = pattern.matcher(body);
            while (named.find()) {
                String written = named.group(1).strip();
                if (!written.isEmpty() && written.indexOf('%') < 0) {
                    found.add(written);
                }
            }
        }
        for (String block : soundBlocks(body)) {
            Matcher key = BLOCK_KEY.matcher(block);
            while (key.find()) {
                found.add(key.group(1).strip());
            }
        }
        return found;
    }

    /**
     * Every block of sounds in this file, from its opening bracket to the one that closes it.
     *
     * <p>The brackets are walked rather than matched, because they nest and a regular expression cannot
     * count. Anything unclosed is taken to the end of the file, which is what a half written file
     * deserves: every sound in it is still read.
     */
    private static List<String> soundBlocks(String body) {
        List<String> blocks = new ArrayList<>();
        Matcher start = BLOCK_START.matcher(body);
        while (start.find()) {
            int open = start.end() - 1;
            int depth = 0;
            int at = open;
            while (at < body.length()) {
                char here = body.charAt(at);
                if (here == '{' || here == '[') {
                    depth++;
                } else if (here == '}' || here == ']') {
                    depth--;
                    if (depth == 0) {
                        break;
                    }
                }
                at++;
            }
            blocks.add(body.substring(open, Math.min(at + 1, body.length())));
        }
        return blocks;
    }

    /** Every sound the platform has, read as names and never as values, so no server is needed. */
    private static Set<String> soundConstants() {
        Set<String> found = new LinkedHashSet<>();
        for (Field field : Sound.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Sound.class) {
                found.add(field.getName());
            }
        }
        return found;
    }

    /** Every file a first boot writes out, apart from the words, which name no sound. */
    private static List<Path> shippedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".conf"))
                    .filter(path -> !path.toString().contains("messages"))
                    .toList();
        }
    }

    private static String withoutComments(String body) {
        StringBuilder kept = new StringBuilder(body.length());
        for (String line : body.split("\n", -1)) {
            if (!line.strip().startsWith("#")) {
                kept.append(line).append('\n');
            }
        }
        return kept.toString();
    }
}
