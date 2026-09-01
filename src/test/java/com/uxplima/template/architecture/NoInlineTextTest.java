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
 * Fails when a source file builds a message out of a literal instead of a key.
 *
 * <p>An inline literal cannot be translated and cannot be re-worded by an operator, so one literal is one
 * string that the plugin can never localise. ArchUnit cannot see this, because it reads bytecode and a
 * literal is not a dependency. So the guard reads the source.
 */
final class NoInlineTextTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

    /** A MiniMessage tag in a literal is the reliable signal that the literal is user-facing text. */
    private static final Pattern MINI_MESSAGE_LITERAL = Pattern.compile("\"[^\"]*<(?:[a-z_]+|#[0-9a-fA-F]{6})[^\"]*\"");

    /**
     * An XML document is not a message.
     *
     * <p>A plugin that speaks WebDAV or writes an XML body holds {@code <d:prop>} in a literal, which reads
     * to the pattern above as a tag. MiniMessage closes a tag as {@code </name>} and opens a document with
     * nothing, so a literal that holds {@code <?xml}, {@code xmlns} or a self-closing {@code />} cannot be
     * text a player sees. The rule names a shape no message has, and not a file that may break the rule.
     */
    private static boolean isMarkup(String literal) {
        return literal.contains("<?xml") || literal.contains("xmlns") || literal.contains("/>");
    }

    /** Where a template literal legitimately lives: the key enum carries every default. */
    private static final String KEY_ENUM = "application/message/Keys.java";

    @Test
    @DisplayName("no source file outside the key enum holds a MiniMessage literal")
    void noInlineMiniMessageLiterals() throws IOException {
        List<String> offenders = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(SOURCE_ROOT)) {
            for (Path file :
                    sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = SOURCE_ROOT.relativize(file).toString().replace('\\', '/');
                if (relative.endsWith(KEY_ENUM)) {
                    continue;
                }
                offenders.addAll(findLiterals(file, relative));
            }
        }

        assertThat(offenders)
                .describedAs("Move each literal into com.uxplima.template.application.message.Keys "
                        + "and send it through Messages, so that it can be translated.")
                .isEmpty();
    }

    private static List<String> findLiterals(Path file, String relative) throws IOException {
        List<String> found = new ArrayList<>();
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);

        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index).strip();
            // A javadoc or a comment may name a tag while it explains one. Only code counts.
            if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                continue;
            }
            Matcher matcher = MINI_MESSAGE_LITERAL.matcher(line);
            if (matcher.find() && !isMarkup(matcher.group())) {
                found.add(relative + ":" + (index + 1) + " -> " + matcher.group());
            }
        }
        return found;
    }
}
