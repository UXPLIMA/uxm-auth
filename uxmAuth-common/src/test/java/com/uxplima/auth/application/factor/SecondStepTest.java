package com.uxplima.auth.application.factor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.factor.FactorKind;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryEmails;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Whether a first step that passed is the whole login, or a code from the app is asked next. */
final class SecondStepTest {

    private final InMemoryFactors factors = new InMemoryFactors();
    private SecondStep.Settings settings = new SecondStep.Settings(true, false, 3);
    private final InMemoryEmails emails = new InMemoryEmails();
    private final List<Account> mailed = new ArrayList<>();
    private final SecondStep step = new SecondStep(factors, emails, () -> settings, mailed::add);
    private final Account steve = AuthFixture.account("Steve", new PasswordHash("$hash$x"));

    private void appOn() {
        factors.setApp(steve.id(), new TotpSecret(new byte[20]));
    }

    @Test
    @DisplayName("an account with no app is logged in at once, or held to replace a weak password")
    void noAppNoStep() {
        assertThat(step.after(steve, AuthMethod.PASSWORD, null))
                .isEqualTo(new AuthState.Authenticated(steve, AuthMethod.PASSWORD));
        PasswordVerdict weak = new PasswordVerdict.TooShort(10);
        assertThat(step.after(steve, AuthMethod.PASSWORD, weak)).isEqualTo(new AuthState.Replacing(steve, weak));
    }

    @Test
    @DisplayName(
            "a right password on an account with the app waits for a code, with fresh tries, and keeps a weak verdict")
    void aPasswordAsksTheCode() {
        appOn();
        PasswordVerdict weak = new PasswordVerdict.TooShort(10);

        assertThat(step.after(steve, AuthMethod.PASSWORD, weak))
                .isEqualTo(new AuthState.SecondFactor(
                        steve, AuthMethod.PASSWORD, Set.of(FactorKind.APP), Attempts.fresh(3), weak));
        assertThat(step.asks(steve, AuthMethod.PASSWORD)).isTrue();
    }

    @Test
    @DisplayName("a remembered session skips the password but asks the code, unless the operator lets sessions skip it")
    void aSessionAsksTheCode() {
        appOn();
        assertThat(step.after(steve, AuthMethod.SESSION, null)).isInstanceOf(AuthState.SecondFactor.class);

        settings = new SecondStep.Settings(true, true, 3);

        assertThat(step.after(steve, AuthMethod.SESSION, null))
                .isEqualTo(new AuthState.Authenticated(steve, AuthMethod.SESSION));
    }

    @Test
    @DisplayName(
            "a premium handshake or Floodgate's word skips the code while premium-skips is on, and asks it when off")
    void premiumSkipsByDefault() {
        appOn();
        assertThat(step.after(steve, AuthMethod.PREMIUM, null)).isInstanceOf(AuthState.Authenticated.class);
        assertThat(step.after(steve, AuthMethod.BEDROCK, null)).isInstanceOf(AuthState.Authenticated.class);

        settings = new SecondStep.Settings(false, false, 3);

        assertThat(step.after(steve, AuthMethod.PREMIUM, null)).isInstanceOf(AuthState.SecondFactor.class);
        assertThat(step.after(steve, AuthMethod.BEDROCK, null)).isInstanceOf(AuthState.SecondFactor.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = AuthMethod.class,
            names = {"REGISTERED", "FORCED", "NETWORK"})
    @DisplayName("a new account, staff's word and the proxy's word never ask: the proxy asked already")
    void someWaysNeverAsk(AuthMethod method) {
        appOn();
        settings = new SecondStep.Settings(false, false, 3);

        assertThat(step.after(steve, method, null)).isEqualTo(new AuthState.Authenticated(steve, method));
        assertThat(step.asks(steve, method)).isFalse();
    }

    @Test
    @DisplayName("with no factor table, nothing is ever asked")
    void noneAsksNothing() {
        assertThat(SecondStep.NONE.after(steve, AuthMethod.PASSWORD, null))
                .isEqualTo(new AuthState.Authenticated(steve, AuthMethod.PASSWORD));
    }

    @Test
    @DisplayName("a player the proxy says owes the code waits for it, unless the app went off meanwhile")
    void theProxysWordIsKept() {
        assertThat(step.awaiting(steve, AuthMethod.SESSION))
                .isEqualTo(new AuthState.Authenticated(steve, AuthMethod.SESSION));

        appOn();
        settings = new SecondStep.Settings(true, true, 3);

        assertThat(step.awaiting(steve, AuthMethod.SESSION))
                .isEqualTo(new AuthState.SecondFactor(
                        steve, AuthMethod.SESSION, Set.of(FactorKind.APP), Attempts.fresh(3), null));
    }

    private void mailOn() {
        emails.setAddress(steve.id(), EmailAddress.parse("steve@example.net").orElseThrow());
        emails.setMailsLoginCode(steve.id(), true);
    }

    @Test
    @DisplayName("an account that takes its code by mail waits for it, and the code is mailed as the step opens")
    void aMailedCodeIsAsked() {
        mailOn();

        assertThat(step.after(steve, AuthMethod.PASSWORD, null))
                .isEqualTo(new AuthState.SecondFactor(
                        steve, AuthMethod.PASSWORD, Set.of(FactorKind.EMAIL), Attempts.fresh(3), null));
        assertThat(mailed).containsExactly(steve);
        assertThat(step.asks(steve, AuthMethod.PASSWORD)).isTrue();
        assertThat(mailed).as("asking mails nothing").hasSize(1);
    }

    @Test
    @DisplayName("an account with the app and the mail takes either, and the code is mailed too")
    void bothAreTaken() {
        appOn();
        mailOn();

        assertThat(step.after(steve, AuthMethod.PASSWORD, null))
                .isInstanceOfSatisfying(
                        AuthState.SecondFactor.class,
                        waiting -> assertThat(waiting.kinds())
                                .containsExactlyInAnyOrder(FactorKind.APP, FactorKind.EMAIL));
        assertThat(step.awaiting(steve, AuthMethod.NETWORK))
                .isInstanceOfSatisfying(
                        AuthState.SecondFactor.class,
                        waiting -> assertThat(waiting.kinds())
                                .containsExactlyInAnyOrder(FactorKind.APP, FactorKind.EMAIL));
        assertThat(mailed).containsExactly(steve, steve);
    }

    @Test
    @DisplayName("a mail code with no linked address left is no factor, and nothing is mailed")
    void noAddressNoMail() {
        emails.setMailsLoginCode(steve.id(), true);

        assertThat(step.after(steve, AuthMethod.PASSWORD, null))
                .isEqualTo(new AuthState.Authenticated(steve, AuthMethod.PASSWORD));
        assertThat(mailed).isEmpty();
    }

    @Test
    @DisplayName("a way the operator lets skip the code mails nothing either")
    void aSkipMailsNothing() {
        mailOn();

        assertThat(step.after(steve, AuthMethod.PREMIUM, null)).isInstanceOf(AuthState.Authenticated.class);
        assertThat(mailed).isEmpty();
    }
}
