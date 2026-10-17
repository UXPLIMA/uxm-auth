package com.uxplima.auth.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * No command argument is Brigadier's {@code word()}.
 *
 * <p>{@code word()} stops at any character outside {@code 0-9 A-Z a-z _ - . +}, and it stops while parsing, before
 * the command can say why. uxm-plots found its own ids, written {@code creative:-1;0}, refused that way, and the same
 * rule refuses {@code minecraft:stone}, {@code #ff0000} and every Turkish letter. Since uxmLib 0.127.0 an
 * {@code @Arg String} reads up to the next space, and {@code Args.token()} does the same for a node built by hand.
 */
final class EveryTypedTokenReachesItsCommandTest {

    private static final Pattern WORD = Pattern.compile("StringArgumentType\\s*\\.\\s*word\\s*\\(\\s*\\)");

    @Test
    @DisplayName("no argument is a word() that refuses a colon, a hash or a Turkish letter")
    void noArgumentIsAWord() throws IOException {
        List<String> found = new ArrayList<>();
        int read = 0;
        for (Path root : ROOTS) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    read++;
                    Matcher matcher = WORD.matcher(Files.readString(file));
                    while (matcher.find()) {
                        found.add(file.getFileName().toString());
                    }
                }
            }
        }
        assertThat(read)
                .describedAs("a scan that read no source proves nothing")
                .isPositive();
        assertThat(found)
                .describedAs("read these with Args.token() or an @Arg String")
                .isEmpty();
    }

    private static final List<Path> ROOTS = List.of(Path.of("src", "main", "java"));
}
