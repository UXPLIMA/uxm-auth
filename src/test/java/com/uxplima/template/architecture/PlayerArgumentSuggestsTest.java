package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * An argument that takes a player by name offers the names.
 *
 * <p>A command that names a {@code Player} gets Brigadier's own player argument and suggests for free. One
 * that acts on somebody who is offline takes a {@code String} and resolves the name itself, and a
 * {@code String} argument suggests nothing at all. Twenty five of those shipped across five plugins and every
 * one of them was typed blind, which the owner found on 2026-09-08 giving a key to a player whose name the
 * console would not complete.
 *
 * <p>uxmLib 0.59.0 ships {@code PlayerNames} and {@code ParamResolvers.withDefaults()} registers it, so the
 * whole of the fix at this end is one annotation. This is what keeps the twenty sixth from shipping without
 * it.
 *
 * <p>It reads the argument's own name and not the parameter's. It looked for {@code String player} until
 * 2026-09-10, and uxmMinions writes {@code @Arg("player") String who}: the argument a player types is
 * called player either way, and what the method calls its parameter is nobody else's business.
 */
class PlayerArgumentSuggestsTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    @Test
    @DisplayName("every player-name argument carries the suggestion the library registers")
    void everyPlayerArgumentSuggests() throws IOException {
        List<String> bare = new ArrayList<>();

        try (Stream<Path> files = Files.walk(SOURCE)) {
            for (Path file :
                    files.filter(path -> path.toString().endsWith(".java")).toList()) {
                int number = 0;
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    number++;
                    if (takesANameBlind(line)) {
                        bare.add(file.getFileName() + ":" + number + " " + line.strip());
                    }
                }
            }
        }

        assertThat(bare)
                .describedAs("write @Arg(\"player\") @SuggestUsing(PlayerNames.KEY) String player. A String "
                        + "argument suggests nothing on its own and the console completes nothing.")
                .isEmpty();
    }

    @Test
    @DisplayName("the reader sees both shapes, so a clean pass means there are none rather than none seen")
    void thereaderSeesBothShapes() {
        // The test above passes when the scan finds nothing, and it finds nothing the day the shape it
        // looks for changes: a rename of the argument, a new annotation order, a different suggestion
        // class. Feeding it one line of each kind is the cheapest proof that it can still tell them apart.
        assertThat(takesANameBlind("public void pay(@Arg(\"player\") String who) {"))
                .describedAs("an argument that takes a name and offers nothing is the whole subject of this"
                        + " guard, so a reader that cannot see one guards nothing")
                .isTrue();
        assertThat(takesANameBlind("public void pay(@Arg(\"player\") @SuggestUsing(PlayerNames.KEY) String who) {"))
                .describedAs("an argument that does carry the suggestion is not an offender")
                .isFalse();
    }

    /**
     * Whether this line declares a player argument that takes a name and offers none.
     *
     * <p>What follows the argument's own annotation is read, rather than the whole line: a method that takes
     * a minion's id and a player takes a {@code String} and a {@code Player} on one line, and asking the
     * line whether it mentions a string at all answers yes for both of them.
     */
    private static boolean takesANameBlind(String line) {
        int at = line.indexOf(PLAYER);
        if (at < 0) {
            return false;
        }
        String rest = line.substring(at + PLAYER.length()).strip();
        boolean suggests = false;
        while (rest.startsWith("@")) {
            suggests = suggests || rest.startsWith("@SuggestUsing");
            int closed = rest.indexOf(')');
            if (closed < 0) {
                return false;
            }
            rest = rest.substring(closed + 1).strip();
        }
        // A Player argument is Brigadier's own and suggests for free. Only a name typed as text is blind.
        return rest.startsWith("String ") && !suggests;
    }

    /** The argument a player types, whatever the method calls its parameter. */
    private static final String PLAYER = "@Arg(\"player\")";
}
