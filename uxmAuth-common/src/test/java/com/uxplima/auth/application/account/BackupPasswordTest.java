package com.uxplima.auth.application.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.CharClass;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.password.PasswordPolicy;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.testing.AuthFixture;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A premium account with no password gets one at random, once, on the operator's rules, stored as a hash. */
final class BackupPasswordTest {

    private final AuthFixture fixture = new AuthFixture(1);
    private PasswordPolicy policy = fixture.policy;
    private boolean on = true;
    private final BackupPassword backup =
            new BackupPassword(fixture.accounts, fixture.hashing, () -> policy, () -> on, new SecureRandom());

    @AfterEach
    void close() {
        fixture.close();
    }

    private UUID account(AccountKind kind, @Nullable String password) {
        Account account = new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                kind,
                kind == AccountKind.PREMIUM ? UUID.randomUUID() : null,
                password == null ? null : fixture.hashing.hash(Password.of(password)),
                AuthFixture.NOW,
                "",
                null,
                null);
        fixture.accounts.seed(account);
        return account.id();
    }

    private boolean opens(UUID id, String password) {
        Account account = fixture.accounts.findById(id).orElseThrow();
        return !(fixture.hashing.verify(Password.of(password), Objects.requireNonNull(account.password()))
                instanceof Verification.Wrong);
    }

    @Test
    @DisplayName("a premium account with no password gets one, stored as a hash that opens it, and never a second")
    void issuedOnce() {
        UUID notch = account(AccountKind.PREMIUM, null);

        Optional<String> issued = backup.issue(notch);

        assertThat(issued)
                .hasValueSatisfying(
                        password -> assertThat(opens(notch, password)).isTrue());
        assertThat(backup.issue(notch)).isEmpty();
    }

    @Test
    @DisplayName("the password passes the operator's rules: their length and the kinds of character they require")
    void followsTheRules() {
        policy = new PasswordPolicy(
                20, 24, Pattern.compile("\\S+"), EnumSet.of(CharClass.DIGIT, CharClass.UPPER, CharClass.LOWER), 0);
        UUID notch = account(AccountKind.PREMIUM, null);

        String issued = backup.issue(notch).orElseThrow();

        assertThat(issued.length()).isBetween(20, 24);
        assertThat(policy.check(Password.of(issued))).isEqualTo(new PasswordVerdict.Accepted());
    }

    @Test
    @DisplayName("rules no random password can pass give none, rather than one the login would refuse")
    void impossibleRulesGiveNone() {
        policy = new PasswordPolicy(6, 64, Pattern.compile("[a-z]+"), EnumSet.of(CharClass.DIGIT), 0);
        UUID notch = account(AccountKind.PREMIUM, null);

        assertThat(backup.issue(notch)).isEmpty();
        assertThat(fixture.accounts.findById(notch).orElseThrow().password()).isNull();
    }

    @Test
    @DisplayName("a cracked or Bedrock account, a premium one with a password, or the setting off, gets nothing")
    void onlyWhenItIsNeededAndOn() {
        assertThat(backup.issue(account(AccountKind.CRACKED, "hunter22"))).isEmpty();
        UUID bedrock = account(AccountKind.BEDROCK, null);
        assertThat(backup.issue(bedrock)).isEmpty();
        assertThat(fixture.accounts.findById(bedrock).orElseThrow().password()).isNull();
        UUID kept = account(AccountKind.PREMIUM, "hunter22");
        assertThat(backup.issue(kept)).isEmpty();
        assertThat(opens(kept, "hunter22")).isTrue();

        on = false;
        UUID off = account(AccountKind.PREMIUM, null);
        assertThat(backup.issue(off)).isEmpty();
        assertThat(fixture.accounts.findById(off).orElseThrow().password()).isNull();
        assertThat(backup.issue(UUID.randomUUID())).isEmpty();
    }
}
