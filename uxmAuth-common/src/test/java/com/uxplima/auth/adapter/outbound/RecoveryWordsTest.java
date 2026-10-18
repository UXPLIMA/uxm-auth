package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.mail.RecoverPassword;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.password.PasswordVerdict;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The answers to a forgotten password read the same on a server and on the proxy. */
final class RecoveryWordsTest {

    private final Messages messages = new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new LocaleSource() {
                @Override
                public Locale defaultLocale() {
                    return Locale.ENGLISH;
                }

                @Override
                public Locale localeOf(Audience viewer) {
                    return Locale.ENGLISH;
                }
            });

    /** The line as text; this catalogue has no styles, so a value reads between the tags around it. */
    private String plain(RecoverPassword.Outcome outcome) {
        return PlainTextComponentSerializer.plainText()
                .serialize(RecoveryWords.line(outcome).orElseThrow().render(messages, Audience.empty()));
    }

    @Test
    @DisplayName(
            "a sent code names the masked address and its minutes; a cooldown its seconds, a part rounded up and never none")
    void theValuesAreFilledIn() {
        assertThat(plain(new RecoverPassword.Outcome.Sent("b***@e***.com", Duration.ofMinutes(15))))
                .contains("b***@e***.com")
                .contains(">15<");
        assertThat(plain(new RecoverPassword.Outcome.Cooldown(Duration.ofMillis(15_200))))
                .contains(">16<");
        assertThat(plain(new RecoverPassword.Outcome.Cooldown(Duration.ZERO))).contains(">1<");
        assertThat(plain(new RecoverPassword.Outcome.Wrong(3))).contains(">3<");
    }

    @Test
    @DisplayName("every answer has its own line but a refused password and a ban, which each side says itself")
    void everyAnswerHasALine() {
        List<RecoverPassword.Outcome> plainOnes = List.of(
                new RecoverPassword.Outcome.NoAddress(),
                new RecoverPassword.Outcome.MailOff(),
                new RecoverPassword.Outcome.MailFailed(),
                new RecoverPassword.Outcome.NotWaiting(),
                new RecoverPassword.Outcome.Recovered(),
                new RecoverPassword.Outcome.Expired(),
                new RecoverPassword.Outcome.NothingPending(),
                new RecoverPassword.Outcome.TooManyAttempts());

        assertThat(plainOnes.stream().map(this::plain).distinct()).hasSize(plainOnes.size());
        assertThat(RecoveryWords.line(new RecoverPassword.Outcome.Rejected(new PasswordVerdict.TooShort(6))))
                .isEmpty();
        assertThat(RecoveryWords.line(new RecoverPassword.Outcome.Banned(Duration.ofMinutes(5))))
                .isEmpty();
        assertThat(RecoveryWords.line(new RecoverPassword.Outcome.Recovered())
                        .orElseThrow()
                        .key())
                .isEqualTo(Keys.RECOVER_DONE);
    }
}
