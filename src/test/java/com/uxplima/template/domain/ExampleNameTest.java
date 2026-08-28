package com.uxplima.template.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The domain is pure, so it needs no server and no mock. This is what every domain test looks like. */
final class ExampleNameTest {

    @Test
    @DisplayName("a name is folded to lower case")
    void foldsToLowerCase() {
        assertThat(new ExampleName("MyName").value()).isEqualTo("myname");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "my-name", "name-2", "0123456789012345678901234567890a"})
    @DisplayName("an allowed name is accepted")
    void acceptsAllowedNames(String value) {
        assertThat(new ExampleName(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "has space", "has_underscore", "has.dot", "0123456789012345678901234567890ab"})
    @DisplayName("a name outside the rule is refused at the boundary")
    void refusesEverythingElse(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ExampleName(value));
    }
}
