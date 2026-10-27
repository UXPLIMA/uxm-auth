package com.uxplima.auth.domain.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    private static final Pattern PRINTABLE = Pattern.compile("[\\x21-\\x7E]+");

    private static PasswordVerdict check(PasswordPolicy policy, String typed) {
        return policy.check(Password.of(typed));
    }

    @Test
    void theLengthIsCheckedFirst() {
        PasswordPolicy policy = new PasswordPolicy(6, 12, PRINTABLE, Set.of(), 0);

        assertThat(check(policy, "abc")).isEqualTo(new PasswordVerdict.TooShort(6));
        assertThat(check(policy, "a".repeat(13))).isEqualTo(new PasswordVerdict.TooLong(12));
        assertThat(check(policy, "abcdef")).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    void charactersOutsideTheAllowedPatternAreRefused() {
        PasswordPolicy policy = new PasswordPolicy(1, 64, PRINTABLE, Set.of(), 0);

        assertThat(check(policy, "with space")).isEqualTo(new PasswordVerdict.BadCharacters());
    }

    @Test
    void missingCharacterClassesAreNamed() {
        PasswordPolicy policy = new PasswordPolicy(1, 64, PRINTABLE, Set.of(CharClass.UPPER, CharClass.DIGIT), 0);

        assertThat(check(policy, "lowercase"))
                .isEqualTo(new PasswordVerdict.Missing(Set.of(CharClass.UPPER, CharClass.DIGIT)));
        assertThat(check(policy, "Lowercase1")).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    void aPasswordLongerThanTheHashCanReadIsRefused() {
        // bcrypt reads 72 bytes; a password past that would match any other with the same start.
        PasswordPolicy policy = new PasswordPolicy(1, 200, Pattern.compile("(?s).*"), Set.of(), 72);

        assertThat(check(policy, "ş".repeat(37))).isEqualTo(new PasswordVerdict.TooLongForTheHash(72));
        assertThat(check(policy, "ş".repeat(36))).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    void aCommonPasswordIsRefusedWhateverItsCase() {
        CommonPasswords common = CommonPasswords.of(List.of("# the usual", "qwerty123", "  password  "));
        PasswordPolicy policy = new PasswordPolicy(1, 64, PRINTABLE, Set.of(), 0, common);

        assertThat(common.size()).isEqualTo(2);
        assertThat(check(policy, "QWERTY123")).isEqualTo(new PasswordVerdict.Common());
        assertThat(check(policy, "password")).isEqualTo(new PasswordVerdict.Common());
        assertThat(check(policy, "qwerty124")).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    void anyPolicyAcceptsEverything() {
        assertThat(check(PasswordPolicy.ANY, "x")).isEqualTo(new PasswordVerdict.Accepted());
    }
}
