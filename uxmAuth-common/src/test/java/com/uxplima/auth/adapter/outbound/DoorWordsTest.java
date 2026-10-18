package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.domain.name.NameVerdict;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.Refusal;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The reason a connection is turned away at the door reads the same on a server and on the proxy. */
final class DoorWordsTest {

    private final DoorWords words = new DoorWords(new Messages(
            new MessageCatalog(Map.of(Locale.ENGLISH, MessageCatalog.defaults(Keys.class)), Locale.ENGLISH),
            new LocaleSource() {
                @Override
                public Locale defaultLocale() {
                    return Locale.ENGLISH;
                }

                @Override
                public Locale localeOf(net.kyori.adventure.audience.Audience viewer) {
                    return Locale.ENGLISH;
                }
            }));

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    @DisplayName("a paid name names the account that moved away from it, and every other refusal its own reason")
    void eachRefusalReadsItsReason() {
        assertThat(plain(words.refusal(Refusal.PAID_NAME, Optional.of(new PlayerName("Notch_")))))
                .contains("Notch_");
        assertThat(plain(words.refusal(Refusal.PAID_NAME, Optional.empty()))).contains("paid");
        assertThat(plain(words.refusal(Refusal.PREMIUM_ADDRESS, Optional.empty())))
                .contains("address");
        assertThat(plain(words.refusal(Refusal.NAME_DISPUTED, Optional.empty())))
                .isNotEqualTo(plain(words.refusal(Refusal.NAME_KEPT, Optional.empty())));
        assertThat(plain(words.refusal(Refusal.LOOKUP_FAILED, Optional.empty())))
                .isNotBlank();
    }

    @Test
    @DisplayName("a refused name reads the rule it broke, with the number or the spelling it needs")
    void eachVerdictReadsItsRule() {
        assertThat(plain(words.name(new NameVerdict.TooLong(15)))).contains("15");
        assertThat(plain(words.name(new NameVerdict.TooShort(3)))).contains("3");
        assertThat(plain(words.name(new NameVerdict.WrongCase(new PlayerName("Notch")))))
                .contains("Notch");
        assertThat(plain(words.name(new NameVerdict.Blocked("admin")))).contains("admin");
    }

    @Test
    @DisplayName("a refusal that runs out says how long is left, in its two largest units")
    void aBanSaysTheTimeLeft() {
        assertThat(plain(words.admission(new com.uxplima.auth.application.protection.Admission.Refused(
                        Keys.KICK_BANNED,
                        Map.of(),
                        java.time.Duration.ofMinutes(90).plusSeconds(20)))))
                .contains("1h 30m")
                .doesNotContain("1h 30m 20s");
        assertThat(plain(words.admission(new com.uxplima.auth.application.protection.Admission.Refused(
                        Keys.KICK_BANNED, Map.of(), java.time.Duration.ofDays(1)))))
                .contains("in <value>1d<body>");
        assertThat(plain(words.admission(new com.uxplima.auth.application.protection.Admission.Refused(
                        Keys.KICK_BANNED, Map.of(), java.time.Duration.ofMillis(400)))))
                .contains("in <value>1s<body>");
    }
}
