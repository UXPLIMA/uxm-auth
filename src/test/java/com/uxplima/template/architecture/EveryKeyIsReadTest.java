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
import java.util.stream.Stream;

import com.uxplima.template.application.message.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Fails when a line ships and nothing ever says it.
 *
 * <p>A key that no code sends and no window names is a promise printed in two languages that a player can
 * never be shown. It costs a translator their time, it tells a reviewer the feature is there, and it is
 * invisible to every other guard in this repository: {@code CatalogStyleTest} proves the two catalogues
 * agree with each other, and {@code MenuFilesTest} proves a window never names a key nobody wrote. Neither
 * of them can see a key nobody reads.
 *
 * <p>uxmCrates shipped one: the reward list's empty state wrote a second line telling the operator to hold
 * an item, and nothing ever drew it, because the create button beside it already says exactly that. The
 * line was taken out of both catalogues rather than found a home, which is the other honest answer.
 *
 * <p>There is one allowance and it is checked rather than asserted. The {@code command.} block is the
 * command layer's own, and uxmLib looks those paths up by name through {@link
 * com.uxplima.uxmlib.command.annotation.CommandMessages#fromCatalogue}, so no constant of ours is ever
 * named in our code. The test below proves the plugin really reaches the code that reads that block, so the
 * excuse cannot outlive the reason for it.
 */
final class EveryKeyIsReadTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");
    private static final Path RESOURCES = Path.of("src", "main", "resources");

    /** The one file a key may only be declared in. */
    private static final String KEY_ENUM = "application/message/Keys.java";

    /**
     * The block uxmLib reads by path rather than through a constant of ours.
     *
     * <p>One block and not two. uxmSkills also excuses {@code menu.lore.}, because it draws its tiles
     * through the library's {@code MenuTiles} and those two paths are looked up by name there. This plugin
     * draws its own and names both constants, so nothing is excused that the code could not name.
     */
    private static final List<String> LIBRARY_BLOCKS = List.of("command.", "menu.lore.");

    @Test
    @DisplayName("the command block is read by the library, which is why it is allowed to be unnamed here")
    void theLibraryReallyReadsTheCommandBlock() throws IOException {
        assertThat(java())
                .describedAs("Nothing reads the command block, so the command.* keys are dead too. A plugin"
                        + " either hands the library the catalogue with CommandMessages.fromCatalogue or"
                        + " implements CommandMessages itself, and one of the two has to be here.")
                .anyMatch(source -> source.contains("CommandMessages.fromCatalogue(")
                        || source.contains("implements CommandMessages"));
    }

    @Test
    @DisplayName("every key we ship is sent by the code or named by a window")
    void everyKeyIsRead() throws IOException {
        List<String> sources = java();
        Set<String> namedInFiles = namesInResources();
        List<String> dead = new ArrayList<>();

        for (Keys key : Keys.values()) {
            if (LIBRARY_BLOCKS.stream().anyMatch(block -> key.path().startsWith(block))) {
                continue;
            }
            if (namedInFiles.contains(key.path()) || usedInCode(sources, key) || builtInCode(sources, key)) {
                continue;
            }
            dead.add(key.name() + " (" + key.path() + ")");
        }

        assertThat(dead)
                .describedAs("Send the key, name it from a window as @<path>, or take it out of both catalogues. "
                        + "A line nobody can read is not a translation, it is a claim.")
                .isEmpty();
    }

    /** One key being declared: the constant, then the path it carries. */
    private static final Pattern DECLARATION = Pattern.compile("^\\s*[A-Z][A-Z0-9_]*\\(\\s*\"");

    /** A path built out of a prefix and an end, as {@code "lobby." + id + ".name"} is. */
    private static final Pattern BUILT =
            Pattern.compile("\"([a-z0-9.-]+\\.)\"\\s*\\+[^;\\n]*?\\+\\s*\"(\\.[a-z0-9.-]+)\"");

    /** A path built from a stem and a number, as {@code "item.bow-lore-" + line} is. */
    private static final Pattern BUILT_FROM_A_STEM = Pattern.compile("\"([a-z0-9.-]+[.-])\"\\s*\\+");

    /** A family read off the enum by its prefix, as the feedback channels are. */
    private static final Pattern BY_PREFIX = Pattern.compile("startsWith\\(\\s*\"([a-z0-9.-]+\\.)\"");

    /** The same, one step apart: the prefix in a local and the local in the call. */
    private static final Pattern PREFIX_LOCAL = Pattern.compile("(\\w+)\\s*=\\s*\"([a-z0-9.-]+\\.)\"\\s*;");

    /**
     * Whether the code builds this path rather than naming the constant that carries it.
     *
     * <p>Three shapes, and all of them are real here. A path is concatenated from an id the operator
     * chose, which is how a lobby's own name is read; a path is built from a stem and a number, which is
     * how the ninth line of an item's lore is found; or a whole family is taken off the enum by its
     * prefix and handed back keyed by what follows, which is how a feedback channel is found. None of the
     * three can name a constant, and none of them is dead.
     */
    private static boolean builtInCode(List<String> sources, Keys key) {
        for (String source : sources) {
            Matcher built = BUILT.matcher(source);
            while (built.find()) {
                if (key.path().startsWith(built.group(1)) && key.path().endsWith(built.group(2))) {
                    return true;
                }
            }
            Matcher stem = BUILT_FROM_A_STEM.matcher(source);
            while (stem.find()) {
                if (key.path().startsWith(stem.group(1))) {
                    return true;
                }
            }
            Matcher family = BY_PREFIX.matcher(source);
            while (family.find()) {
                if (key.path().startsWith(family.group(1))) {
                    return true;
                }
            }
            Matcher held = PREFIX_LOCAL.matcher(source);
            while (held.find()) {
                if (source.contains("startsWith(" + held.group(1) + ")")
                        && key.path().startsWith(held.group(2))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether the constant is named anywhere outside the enum that declares it.
     *
     * <p>The name and not the path, because the path is what an operator edits and the constant is what a
     * caller sends. A key referenced only by its own declaration is a key nothing sends.
     */
    private static boolean usedInCode(List<String> sources, Keys key) {
        String named = "Keys." + key.name();
        String bare = key.name();
        for (String source : sources) {
            if (source.contains(named)) {
                return true;
            }
            // A switch over Keys inside the message package names the constant without the type, as
            // TermKeys does. A word boundary keeps MENU_ABILITY_OPEN from matching MENU_ABILITY_OPENED.
            //
            // The package declaration and not a mention of the package. Reading it as a mention let any
            // file that imports Keys claim a key it never sends, and one did: uxmCrates has a Refusal
            // enum with a NO_PERMISSION of its own, a presenter that switches over it beside an import
            // of Keys, and a dead error.no-permission key that this guard called alive for months.
            if (source.contains("package com.uxplima.template.application.message;") && namesBare(source, bare)) {
                return true;
            }
        }
        return false;
    }

    private static boolean namesBare(String source, String name) {
        int at = source.indexOf(name);
        while (at >= 0) {
            boolean beforeIsFree = at == 0 || !Character.isJavaIdentifierPart(source.charAt(at - 1));
            int after = at + name.length();
            boolean afterIsFree = after >= source.length() || !Character.isJavaIdentifierPart(source.charAt(after));
            if (beforeIsFree && afterIsFree) {
                return true;
            }
            at = source.indexOf(name, at + 1);
        }
        return false;
    }

    /**
     * Every path a shipped file names, including the block a tile draws and the ones it offers.
     *
     * <p>A window writes {@code @menu.ability} and the tile behind it holds {@code menu.ability.locked}, so
     * a name in a file covers every key under it. That is the same reading {@code MenuFilesTest} does in the
     * other direction.
     */
    private static Set<String> namesInResources() throws IOException {
        Set<String> named = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(RESOURCES)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                if (file.startsWith(RESOURCES.resolve("messages"))) {
                    continue;
                }
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    // Comments count. A shipped file teaches by example, and an example an operator
                    // uncomments is the tile it draws: uxmCosmetics ships the way to its mystery boxes
                    // commented out with a paragraph saying why, and the words for it have to exist
                    // before they take the hashes off.
                    collectNames(line, named);
                }
            }
        }
        return named;
    }

    private static void collectNames(String line, Set<String> into) {
        int at = line.indexOf('@');
        while (at >= 0) {
            int end = at + 1;
            // The per cent sign is part of the name here, not a boundary: a tile whose block is decided
            // at runtime is written @menu.colour.%colour_state% and the placeholder is what says so.
            while (end < line.length()
                    && (Character.isLetterOrDigit(line.charAt(end)) || ".-_%".indexOf(line.charAt(end)) >= 0)) {
                end++;
            }
            String name = line.substring(at + 1, end);
            if (!name.isEmpty()) {
                // Three spellings. The exact key a window names; the tile block every key under it
                // belongs to, because a window that names @menu.ability reads menu.ability.locked with
                // it; and the keys a placeholder in the name stands for, because a tile whose block is
                // chosen at runtime is written @menu.colour.%colour_state% and names three blocks.
                into.add(name);
                for (Keys key : Keys.values()) {
                    if (key.path().startsWith(name + ".") || standsFor(name, key.path())) {
                        into.add(key.path());
                    }
                }
            }
            at = line.indexOf('@', end);
        }
    }

    /**
     * Whether a name with a placeholder in it stands for this key.
     *
     * <p>A tile whose block is decided at runtime is named {@code @menu.colour.%colour_state%}, and the
     * three blocks it can be are as read as any name written out. The placeholder stands for one segment,
     * never for a dot, so a name cannot quietly cover a whole subtree it does not mean.
     */
    private static boolean standsFor(String name, String path) {
        if (name.indexOf('%') < 0) {
            return false;
        }
        StringBuilder pattern = new StringBuilder();
        for (String piece : name.split("%[^%]*%", -1)) {
            if (pattern.length() > 0) {
                pattern.append("[^.]+");
            }
            pattern.append(Pattern.quote(piece));
        }
        String whole = pattern.toString();
        return path.matches(whole) || path.matches(whole + "\\..*");
    }

    private static List<String> java() throws IOException {
        List<String> sources = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = withoutComments(Files.readString(file, StandardCharsets.UTF_8));
                sources.add(file.toString().replace('\\', '/').endsWith(KEY_ENUM) ? withoutDeclarations(text) : text);
            }
        }
        return sources;
    }

    /**
     * The source with every comment taken out, string literals left alone.
     *
     * <p>A javadoc is not a sender. Without this, a key enum whose class comment names a constant as the
     * example of the naming convention vouches for that constant, and uxmEssentials had exactly that:
     * MUTE_APPLIED read as alive off the sentence explaining what a key is called, while nothing sent it.
     *
     * <p>Written as a walk rather than a pattern: the obvious block-comment regular expression backtracks
     * deeply enough on a large file to overflow the stack.
     */
    private static String withoutComments(String source) {
        StringBuilder kept = new StringBuilder(source.length());
        int at = 0;
        while (at < source.length()) {
            char here = source.charAt(at);
            char next = at + 1 < source.length() ? source.charAt(at + 1) : '\0';
            if (here == '"') {
                at = copyLiteral(source, at, '"', kept);
            } else if (here == '\'') {
                at = copyLiteral(source, at, '\'', kept);
            } else if (here == '/' && next == '/') {
                while (at < source.length() && source.charAt(at) != '\n') {
                    at++;
                }
            } else if (here == '/' && next == '*') {
                at += 2;
                while (at + 1 < source.length() && !(source.charAt(at) == '*' && source.charAt(at + 1) == '/')) {
                    at++;
                }
                at = Math.min(at + 2, source.length());
                kept.append(' ');
            } else {
                kept.append(here);
                at++;
            }
        }
        return kept.toString();
    }

    /** Copy the literal that starts at {@code at} and ends at the next unescaped {@code quote}. */
    private static int copyLiteral(String source, int at, char quote, StringBuilder kept) {
        kept.append(source.charAt(at));
        int index = at + 1;
        while (index < source.length()) {
            char character = source.charAt(index);
            kept.append(character);
            if (character == '\\' && index + 1 < source.length()) {
                kept.append(source.charAt(index + 1));
                index += 2;
                continue;
            }
            index++;
            if (character == quote) {
                break;
            }
        }
        return index;
    }

    /**
     * The key enum with the lines that declare a key taken out.
     *
     * <p>The enum was skipped whole once, and that read two shapes as dead. A gadget picks a line out of a
     * list the enum itself holds, and a feedback channel is looked up by a prefix over {@code values()}:
     * both are the enum naming its own constants for a caller outside it, which is a use like any other.
     * What must not count is the declaration itself, so those lines go and the rest stays.
     */
    private static String withoutDeclarations(String enumSource) {
        StringBuilder kept = new StringBuilder(enumSource.length());
        for (String line : enumSource.split("\\n", -1)) {
            if (!DECLARATION.matcher(line).find()) {
                kept.append(line);
            }
            kept.append('\n');
        }
        return kept.toString();
    }
}
