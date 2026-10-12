package com.uxplima.template.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Locale;

import org.bukkit.entity.Player;

import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.properties.HasName;
import com.tngtech.archunit.core.domain.properties.HasOwner;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A player's language is asked of the resolver, never of their client.
 *
 * <p>The resolver knows what the client does not: the language the server forces, the one the player chose in
 * any plugin of ours, and whether the server follows the client at all. A line that reads
 * {@code player.locale()} skips all three. uxmCrates did it in four places, so a German-only server with
 * {@code follow-client = false} still named a crate's rewards in English to a player whose client is English.
 *
 * <p>The language listener is the one class that reads the client, because remembering what the client says
 * is its whole job.
 */
@AnalyzeClasses(packages = "com.uxplima.template", importOptions = ImportOption.DoNotIncludeTests.class)
final class NoClientLanguageIsReadTest {

    @ArchTest
    static final ArchRule nothingReadsTheClientLanguage = noClasses()
            .that()
            .doNotHaveSimpleName("LanguageListener")
            .should()
            .callMethodWhere(JavaCall.Predicates.target(
                            HasName.Predicates.name("locale").or(HasName.Predicates.name("getLocale")))
                    .and(JavaCall.Predicates.target(
                            HasOwner.Predicates.With.owner(JavaClass.Predicates.assignableTo(Player.class)))))
            .because("the resolver answers which language a player reads: messages.localeOf(player)");

    @Test
    @DisplayName("the rule sees a class that reads the client")
    void theRuleCanFail() {
        assertThatThrownBy(() -> nothingReadsTheClientLanguage.check(
                        new ClassFileImporter().importClasses(ReadsTheClient.class)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ReadsTheClient");
    }

    /** What the rule exists to stop. */
    static final class ReadsTheClient {

        Locale of(Player player) {
            return player.locale();
        }
    }
}
