package com.uxplima.auth.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmlib.text.language.LanguageFileUpdates;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The promise the shipped language files make to a server that was installed before a line was corrected.
 *
 * <p>The files were written once, on the first start, so a line a release corrected stayed wrong on every server
 * that had the old one: the operator's file won over the jar, although the operator never wrote that line. Every
 * plugin of ours closes that through uxmLib's {@code LanguageFileUpdates}. The first test drives it against the
 * English file this jar really ships; the second reads the boot for the call, which is one line a refactor could
 * drop without any other test noticing.
 */
final class LanguageReachesAnOldServerTest {

    /** The two boots, which sit beside this module: each platform writes its own copy of the files. */
    private static final List<Path> BOOTS = List.of(
            Path.of("..", "uxmAuth-paper", "src", "main", "java"),
            Path.of("..", "uxmAuth-velocity", "src", "main", "java"));

    private static final String MESSAGES = "messages";
    private static final String ENGLISH = "messages_en.conf";
    private static final String OLDER_WORDS = "\"uxm-guard-words-of-an-older-release\"";
    private static final Pattern ONE_LINE = Pattern.compile(
            "^(\\s*(?:\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z0-9_\\-.]+)\\s*[=:]\\s*)(\"(?!\"\")(?:[^\"\\\\]|\\\\.)*\")(.*)$");

    @Test
    @DisplayName("a line nobody touched since an older release takes the words this release ships")
    void anUntouchedLineTakesTheShippedWords(@TempDir Path folder) throws IOException {
        String shipped = shippedEnglish();
        String older = withOneLineFromAnOlderRelease(shipped);
        Files.createDirectories(folder.resolve(LanguageFileUpdates.BASELINE_FOLDER));
        Files.writeString(folder.resolve(ENGLISH), older, StandardCharsets.UTF_8);
        Files.writeString(
                folder.resolve(LanguageFileUpdates.BASELINE_FOLDER).resolve(ENGLISH), older, StandardCharsets.UTF_8);

        LanguageFileUpdates.Report report =
                LanguageFileUpdates.bringUpToDate(getClass().getClassLoader(), MESSAGES, folder);

        assertThat(report.updated()).hasSize(1);
        assertThat(Files.readString(folder.resolve(ENGLISH), StandardCharsets.UTF_8))
                .isEqualTo(shipped);
    }

    @Test
    @DisplayName("both boots bring the language files up to date, so the correction is not a method nobody calls")
    void theBootBringsTheLanguageFilesUpToDate() throws IOException {
        for (Path boot : BOOTS) {
            List<Path> callers = new ArrayList<>();
            try (Stream<Path> files = Files.walk(boot)) {
                for (Path source :
                        files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    if (Files.readString(source, StandardCharsets.UTF_8)
                            .contains("LanguageFileUpdates.bringUpToDate(")) {
                        callers.add(source);
                    }
                }
            }

            assertThat(callers)
                    .describedAs(
                            "%s never calls LanguageFileUpdates, so a corrected line stays wrong on every server"
                                    + " that had the old one",
                            boot)
                    .isNotEmpty();
        }
    }

    private String shippedEnglish() throws IOException {
        try (InputStream shipped = getClass().getClassLoader().getResourceAsStream(MESSAGES + "/" + ENGLISH)) {
            assertThat(shipped)
                    .describedAs("the jar ships %s/%s", MESSAGES, ENGLISH)
                    .isNotNull();
            return new String(Objects.requireNonNull(shipped).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The shipped file with its first one-line value as an older release would have written it. */
    private static String withOneLineFromAnOlderRelease(String shipped) {
        String[] lines = shipped.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            Matcher line = ONE_LINE.matcher(lines[index]);
            if (line.matches()) {
                lines[index] = line.group(1) + OLDER_WORDS + line.group(3);
                return String.join("\n", lines);
            }
        }
        throw new AssertionError("the shipped " + ENGLISH + " holds no one-line value for this guard to change");
    }
}
