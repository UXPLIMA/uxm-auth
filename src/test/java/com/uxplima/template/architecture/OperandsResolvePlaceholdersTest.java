package com.uxplima.template.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * Fails when a condition list or an action list is read with the operands left as they were written.
 *
 * <p>Every line an operator writes into a requirement or a reward is read by the uxmLib condition engine,
 * and the engine asks a resolver what each side of the comparison means before it compares them. Handing it
 * {@code OperandResolver.identity()} says every operand means itself, so {@code %vault_eco_balance%} is
 * compared as those twenty characters rather than as the number the player holds. The line does not fail
 * and nothing is logged: it is simply never true, and the operator has only the code to tell them why.
 *
 * <p>uxmCrates shipped that in three places while the rest of the estate resolved placeholders in all of
 * theirs, which is how this guard came to be written. Nothing else can see the difference, because both
 * spellings compile and a server without PlaceholderAPI behaves identically under either.
 */
final class OperandsResolvePlaceholdersTest {

    private static final Path SOURCE = Path.of("src", "main", "java");

    /** Building a request or a context, which is where the resolver is chosen. */
    private static final Pattern BUILDER = Pattern.compile("\\b(ConditionRequest|ActionContext)\\.builder\\(\\s*");

    /**
     * The identity resolver, however it is spelled.
     *
     * <p>An optional package qualifier in front of it, because a fully qualified call is the same call and
     * a guard a rename can walk around is not a guard.
     */
    private static final Pattern IDENTITY = Pattern.compile("(?:[\\w.]*\\.)?OperandResolver\\.identity\\(\\)");

    @Test
    @DisplayName("no condition list and no action list is read with the operands left unresolved")
    void nothingReadsAListWithIdentityOperands() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(SOURCE)) {
            for (Path file :
                    sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                offenders.addAll(unresolvedIn(file));
            }
        }

        assertThat(offenders)
                .describedAs("Resolve the operands, so a placeholder an operator wrote means what it says.")
                .isEmpty();
    }

    private static List<String> unresolvedIn(Path file) throws IOException {
        List<String> offenders = new ArrayList<>();
        String text = Files.readString(file, StandardCharsets.UTF_8);
        Matcher builder = BUILDER.matcher(text);
        while (builder.find()) {
            Matcher identity = IDENTITY.matcher(text);
            if (identity.find(builder.end()) && identity.start() == builder.end()) {
                offenders.add(file.getFileName() + ", line " + lineOf(text, builder.start()));
            }
        }
        return offenders;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int at = 0; at < index; at++) {
            if (text.charAt(at) == '\n') {
                line++;
            }
        }
        return line;
    }
}
