package com.uxplima.auth.domain.name;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class NamePolicyTest {

    private final NamePolicy policy = new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of("admin"));

    @Test
    void aNormalNameIsAccepted() {
        assertThat(policy.check("Steve_42")).isEqualTo(new NameVerdict.Accepted(new PlayerName("Steve_42")));
    }

    @Test
    void lengthAndCharactersAreChecked() {
        assertThat(policy.check("ab")).isEqualTo(new NameVerdict.TooShort(3));
        assertThat(policy.check("a".repeat(17))).isEqualTo(new NameVerdict.TooLong(16));
        assertThat(policy.check("bad-name")).isEqualTo(new NameVerdict.BadCharacters());
    }

    @Test
    void aBlockedWordIsFoundAnywhereInAnyCase() {
        assertThat(policy.check("xXAdMiNXx")).isEqualTo(new NameVerdict.Blocked("admin"));
    }

    @Test
    void twoSpellingsOfANameShareOneKey() {
        assertThat(new PlayerName("Steve").key()).isEqualTo(new PlayerName("steve").key());
    }
}
