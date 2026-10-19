package com.uxplima.auth.paper.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.paper.testing.Modules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Each key the provider answers is written where an operator looks for it: the subject document, and the comment
 * above the {@code placeholders} block of {@code config.conf}, which is the file they have open when they pick a
 * date format. A placeholder has no window and no command, so a key on neither list is one nobody types.
 */
final class EveryPlaceholderIsWrittenDownTest {

    private static final Path PROVIDER =
            Path.of("src/main/java/com/uxplima/auth/paper/adapter/inbound/placeholder/AuthPlaceholders.java");

    /** A key is an arm of the provider's switch. */
    private static final Pattern ARM = Pattern.compile("case\\s+\"([a-z0-9_]+)\"\\s*->");

    private static Set<String> answered() throws IOException {
        Matcher arms = ARM.matcher(Files.readString(PROVIDER, StandardCharsets.UTF_8));
        Set<String> keys = new LinkedHashSet<>();
        while (arms.find()) {
            keys.add(arms.group(1));
        }
        return keys;
    }

    @Test
    @DisplayName("the provider answers keys, so the lists below are held against something")
    void theProviderAnswersKeys() throws IOException {
        assertThat(answered()).contains("authenticated", "2fa", "last_login");
    }

    @Test
    @DisplayName("every key the provider answers is in the README, written as a placeholder")
    void everyKeyIsInTheDocument() throws IOException {
        String document = Files.readString(Modules.repositoryRoot().resolve("README.md"), StandardCharsets.UTF_8);

        for (String key : answered()) {
            assertThat(document).as(key).contains("%uxmauth_" + key + "%");
        }
    }

    @Test
    @DisplayName("every key the provider answers is in the comment above the placeholders block of config.conf")
    void everyKeyIsInTheConfig() throws IOException {
        String config = Files.readString(Modules.commonResources().resolve("config.conf"), StandardCharsets.UTF_8);
        String comment = config.substring(0, config.indexOf("\nplaceholders {"));

        for (String key : answered()) {
            assertThat(comment).as(key).contains("%uxmauth_" + key + "%");
        }
    }
}
