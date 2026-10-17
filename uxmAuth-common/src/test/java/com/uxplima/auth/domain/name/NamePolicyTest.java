package com.uxplima.auth.domain.name;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class NamePolicyTest {

    private static final Pattern JAVA_NAME = Pattern.compile("^[A-Za-z0-9_]+$");

    private static NamePolicy policy(List<String> blocked) {
        return new NamePolicy(JAVA_NAME, 3, 16, blocked);
    }

    @Test
    @DisplayName("a name shorter than the minimum is refused, with the minimum")
    void refusesANameShorterThanTheMinimum() {
        assertThat(policy(List.of()).check("ab")).isEqualTo(new NameVerdict.TooShort(3));
    }

    @Test
    @DisplayName("a name longer than the maximum is refused, with the maximum")
    void refusesANameLongerThanTheMaximum() {
        assertThat(policy(List.of()).check("a".repeat(17))).isEqualTo(new NameVerdict.TooLong(16));
    }

    @Test
    @DisplayName("a space is a character the pattern does not allow")
    void refusesASpace() {
        assertThat(policy(List.of()).check("a b")).isEqualTo(new NameVerdict.BadCharacters());
    }

    @Test
    @DisplayName("a blocked word is refused inside a name and in any case")
    void refusesABlockedWordInAnyCase() {
        assertThat(policy(List.of("admin")).check("xAdMinx")).isEqualTo(new NameVerdict.Blocked("admin"));
    }

    @Test
    @DisplayName("an accepted name keeps the case it was typed in, and its key is lower case")
    void acceptsAndKeepsTheCase() {
        NameVerdict verdict = policy(List.of()).check("Notch");

        assertThat(verdict).isInstanceOf(NameVerdict.Accepted.class);
        PlayerName name = ((NameVerdict.Accepted) verdict).name();
        assertThat(name.value()).isEqualTo("Notch");
        assertThat(name.key()).isEqualTo("notch");
    }

    @Test
    @DisplayName("the key folds a capital I to i even when the server runs in Turkish")
    void turkishCapitalIDoesNotFoldWithTheDefaultLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(new PlayerName("ILIKE").key()).isEqualTo("ilike");
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    @DisplayName("two names that differ only in case share one key")
    void caseDoesNotMakeTwoKeys() {
        assertThat(new PlayerName("Notch").key()).isEqualTo(new PlayerName("NOTCH").key());
    }
}
