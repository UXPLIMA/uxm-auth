package com.uxplima.auth.domain.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A password on the operator's list of common ones is refused, whatever its case. */
final class CommonPasswordsTest {

    private final CommonPasswords common =
            CommonPasswords.of(List.of("password", "Qwerty123", "  minecraft  ", "", "#"));

    @Test
    @DisplayName("a listed password matches in any case, and one that only starts or ends like it does not")
    void listedPasswordsMatch() {
        assertThat(common.contains(Password.of("password"))).isTrue();
        assertThat(common.contains(Password.of("PassWord"))).isTrue();
        assertThat(common.contains(Password.of("qwerty123"))).isTrue();
        assertThat(common.contains(Password.of("MINECRAFT"))).isTrue();
        assertThat(common.contains(Password.of("password1"))).isFalse();
        assertThat(common.contains(Password.of("passwor"))).isFalse();
        assertThat(common.contains(Password.of("hunter22"))).isFalse();
    }

    @Test
    @DisplayName("blank lines and lines starting with # are no passwords, and an empty list matches nothing")
    void blankLinesAndComments() {
        assertThat(common.size()).isEqualTo(3);
        assertThat(CommonPasswords.none().contains(Password.of("password"))).isFalse();
    }

    @Test
    @DisplayName("the policy refuses a common password once every other rule passed")
    void thePolicyRefusesACommonPassword() {
        PasswordPolicy policy = new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 0, common);

        assertThat(policy.check(Password.of("Password"))).isEqualTo(new PasswordVerdict.Common());
        assertThat(policy.check(Password.of("pass"))).isEqualTo(new PasswordVerdict.TooShort(6));
        assertThat(policy.check(Password.of("hunter22"))).isEqualTo(new PasswordVerdict.Accepted());
        assertThat(new PasswordPolicy(6, 64, Pattern.compile("\\S+"), Set.of(), 0).check(Password.of("password")))
                .isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    @DisplayName("two lists of the same passwords are equal, and the text of a list names only its size")
    void equalityAndText() {
        assertThat(CommonPasswords.of(List.of("Password", "letmein")))
                .isEqualTo(CommonPasswords.of(List.of("letmein", "password")))
                .hasSameHashCodeAs(CommonPasswords.of(List.of("letmein", "password")))
                .isNotEqualTo(CommonPasswords.of(List.of("letmein")));
        assertThat(CommonPasswords.of(List.of("dragon", "monkey")))
                .isEqualTo(CommonPasswords.of(List.of("monkey", "dragon")));
        assertThat(CommonPasswords.none()).isEqualTo(CommonPasswords.none());
        assertThat(common.toString()).doesNotContain("password").contains("3");
    }
}
