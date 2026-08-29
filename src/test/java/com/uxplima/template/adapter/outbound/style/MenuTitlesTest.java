package com.uxplima.template.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class MenuTitlesTest {

    @Test
    @DisplayName("a title is padded into the middle of the window")
    void centresATitle() {
        String plain = plain(MenuTitles.centre(Component.text("ᴛᴀɢꜱ")));
        assertThat(plain).startsWith(" ").endsWith("ᴛᴀɢꜱ");
        assertThat(plain.length()).isGreaterThan("ᴛᴀɢꜱ".length());
    }

    @Test
    @DisplayName("a colour on a title is dropped, so no window is painted in two tones")
    void dropsColour() {
        Component centred = MenuTitles.centre(Component.text("tags", NamedTextColor.RED));
        assertThat(centred.color()).isNull();
    }

    @Test
    @DisplayName("a title wider than the window is not padded")
    void doesNotPadALongTitle() {
        String long_ = "a".repeat(80);
        assertThat(plain(MenuTitles.centre(Component.text(long_)))).isEqualTo(long_);
    }

    @Test
    @DisplayName("a blank title is handed back, because a window titled with spaces is worse")
    void keepsABlankTitle() {
        assertThat(plain(MenuTitles.centre(Component.empty()))).isEmpty();
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }
}
