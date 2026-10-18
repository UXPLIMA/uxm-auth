package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.auth.application.message.Keys;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A login or a registration another plugin refused reads that plugin's reason, or ours when it gave none. */
final class RefusalWordsTest {

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

    private String plain(String reason, Keys fallback) {
        return PlainTextComponentSerializer.plainText()
                .serialize(RefusalWords.of(messages, Audience.empty(), reason, fallback));
    }

    @Test
    @DisplayName("the listener's reason is read as it was written, tags and all, never as MiniMessage")
    void theReasonIsReadAsWritten() {
        assertThat(plain("Closed for <b>maintenance</b>.", Keys.LOGIN_REFUSED))
                .isEqualTo("Closed for <b>maintenance</b>.");
    }

    @Test
    @DisplayName("no reason reads our own words for a login and for a registration")
    void noReasonReadsOurs() {
        assertThat(plain("", Keys.LOGIN_REFUSED)).contains("Your login was refused.");
        assertThat(plain("  ", Keys.REGISTER_REFUSED)).contains("Your registration was refused.");
    }
}
