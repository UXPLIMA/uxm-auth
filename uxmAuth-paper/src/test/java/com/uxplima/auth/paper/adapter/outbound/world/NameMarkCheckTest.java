package com.uxplima.auth.paper.adapter.outbound.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.DomainMark;
import com.uxplima.auth.domain.premium.NameRule;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.premium.WhenUnknown;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A mark Paper refuses in a name works only with Paper's name check off, and the start says so. */
final class NameMarkCheckTest {

    private static AuthSettings.Ownership ownership(
            NameRule rule, String crackedMark, ConflictStrategy conflict, String renameMark) {
        return new AuthSettings.Ownership(
                rule,
                WhenUnknown.CRACKED,
                Duration.ofMinutes(5),
                Duration.ofMinutes(10),
                rule == NameRule.DOMAIN ? List.of("premium.example.org") : List.of(),
                List.of(),
                UuidScheme.KEEP_FIRST,
                conflict,
                renameMark,
                new DomainMark(crackedMark, false),
                true);
    }

    @Test
    @DisplayName("the domain rule's mark Paper refuses is named while Paper checks names, and not once it does not")
    void theDomainMarkIsNamed() {
        AuthSettings.Ownership domain = ownership(NameRule.DOMAIN, "+", ConflictStrategy.RENAME, "_");

        assertThat(NameMarkCheck.warnings(true, domain))
                .singleElement()
                .satisfies(line -> assertThat(line)
                        .contains("ownership.domains.cracked-mark")
                        .contains("\"+\"")
                        .contains("perform-username-validation"));
        assertThat(NameMarkCheck.warnings(false, domain)).isEmpty();
        assertThat(NameMarkCheck.warnings(true, ownership(NameRule.DOMAIN, "c_", ConflictStrategy.RENAME, "_")))
                .isEmpty();
    }

    @Test
    @DisplayName("a mark a rule or a strategy never uses is not named; the rename mark is, while renaming is on")
    void onlyAMarkInUseIsNamed() {
        assertThat(NameMarkCheck.warnings(true, ownership(NameRule.AUTOMATIC, "+", ConflictStrategy.RENAME, "_")))
                .isEmpty();
        assertThat(NameMarkCheck.warnings(true, ownership(NameRule.AUTOMATIC, "+", ConflictStrategy.RENAME, "!")))
                .singleElement()
                .satisfies(line ->
                        assertThat(line).contains("ownership.rename-mark").contains("\"!\""));
        assertThat(NameMarkCheck.warnings(true, ownership(NameRule.AUTOMATIC, "+", ConflictStrategy.BLOCK, "!")))
                .isEmpty();
    }

    @Test
    @DisplayName("both marks refused are both named at once, so the operator fixes them in one restart")
    void bothRefusedMarksAreNamed() {
        assertThat(NameMarkCheck.warnings(true, ownership(NameRule.DOMAIN, "+", ConflictStrategy.RENAME, "!")))
                .satisfiesExactly(
                        first -> assertThat(first).contains("ownership.domains.cracked-mark"),
                        second -> assertThat(second).contains("ownership.rename-mark"));
    }

    @Test
    @DisplayName("Paper's setting is read from its global file, and a file that does not say counts as checking")
    void paperSettingIsRead(@TempDir Path root) throws IOException {
        assertThat(NameMarkCheck.paperChecksNames(root)).isTrue();

        Files.createDirectories(root.resolve("config"));
        Files.writeString(
                root.resolve("config/paper-global.yml"),
                "unsupported-settings:\n  perform-username-validation: false\n  skip-vanilla: true\n");
        assertThat(NameMarkCheck.paperChecksNames(root)).isFalse();

        Files.writeString(
                root.resolve("config/paper-global.yml"),
                "unsupported-settings:\n  perform-username-validation: true\n");
        assertThat(NameMarkCheck.paperChecksNames(root)).isTrue();
    }
}
