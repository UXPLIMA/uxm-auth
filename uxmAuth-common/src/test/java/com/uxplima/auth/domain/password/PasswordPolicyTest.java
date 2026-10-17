package com.uxplima.auth.domain.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class PasswordPolicyTest {

    private static final Pattern ANY = Pattern.compile("\\S*");

    private static PasswordPolicy policy(Set<CharClass> required, int maxBytes) {
        return new PasswordPolicy(6, 64, ANY, required, maxBytes);
    }

    private static PasswordVerdict check(PasswordPolicy policy, String raw) {
        try (Password password = Password.of(raw)) {
            return policy.check(password);
        }
    }

    @Test
    @DisplayName("a password shorter than the minimum is refused")
    void tooShort() {
        assertThat(check(policy(Set.of(), 0), "abc")).isEqualTo(new PasswordVerdict.TooShort(6));
    }

    @Test
    @DisplayName("a password longer than the maximum in characters is refused")
    void tooLong() {
        assertThat(check(policy(Set.of(), 0), "a".repeat(65))).isEqualTo(new PasswordVerdict.TooLong(64));
    }

    @Test
    @DisplayName("a character outside the allowed pattern is refused")
    void badCharacters() {
        assertThat(check(policy(Set.of(), 0), "with space")).isEqualTo(new PasswordVerdict.BadCharacters());
    }

    @Test
    @DisplayName("a missing character class is named")
    void missingClass() {
        PasswordPolicy policy = policy(EnumSet.of(CharClass.UPPER, CharClass.DIGIT), 0);

        assertThat(check(policy, "lowercase"))
                .isEqualTo(new PasswordVerdict.Missing(EnumSet.of(CharClass.UPPER, CharClass.DIGIT)));
        assertThat(check(policy, "Lowercase1")).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    @DisplayName("the hash's byte limit is counted in bytes, so a Turkish letter counts twice")
    void bcryptLimitIsCountedInBytes() {
        PasswordPolicy policy = policy(Set.of(), 72);

        assertThat(check(policy, "ş".repeat(40))).isEqualTo(new PasswordVerdict.TooLongForTheHash(72));
        assertThat(check(policy, "ş".repeat(36))).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    @DisplayName("length is counted in characters a player sees, not in UTF-16 units")
    void lengthCountsCodePoints() {
        assertThat(check(policy(Set.of(), 0), "😀😀😀")).isEqualTo(new PasswordVerdict.TooShort(6));
    }

    @Test
    @DisplayName("a password never prints, and closing it wipes it")
    void passwordNeverPrints() {
        Password password = Password.of("hunter2secret");

        assertThat(String.valueOf(password)).doesNotContain("hunter2");
        password.close();
        assertThat(password.isWiped()).isTrue();
    }
}
