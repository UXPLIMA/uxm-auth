package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
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

/** A time left reads in the units of the reader's language, the two largest first, a part second as a second. */
final class TimeWordsTest {

    private final TimeWords words = new TimeWords(new Messages(
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
            }));

    private String plain(Duration left) {
        return PlainTextComponentSerializer.plainText().serialize(words.left(Audience.empty(), left));
    }

    @Test
    @DisplayName("days and hours, hours and minutes, minutes and seconds, each pair the two largest")
    void theTwoLargestUnits() {
        assertThat(plain(Duration.ofDays(2).plusHours(3).plusMinutes(4))).isEqualTo("2d 3h");
        assertThat(plain(Duration.ofHours(1).plusMinutes(30).plusSeconds(20))).isEqualTo("1h 30m");
        assertThat(plain(Duration.ofMinutes(4).plusSeconds(30))).isEqualTo("4m 30s");
        assertThat(plain(Duration.ofMinutes(5))).isEqualTo("5m");
        assertThat(plain(Duration.ofHours(1).plusSeconds(5))).isEqualTo("1h");
    }

    @Test
    @DisplayName("a part of a second reads as a second, and nothing left reads as none")
    void partSecondsRoundUp() {
        assertThat(plain(Duration.ofMillis(1))).isEqualTo("1s");
        assertThat(plain(Duration.ofSeconds(59).plusMillis(1))).isEqualTo("1m");
        assertThat(plain(Duration.ZERO)).isEqualTo("0s");
    }
}
