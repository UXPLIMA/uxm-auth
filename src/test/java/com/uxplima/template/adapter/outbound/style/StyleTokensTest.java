package com.uxplima.template.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class StyleTokensTest {

    private final Theme theme = Theme.defaults();

    @Test
    @DisplayName("a colour token becomes the colour the theme holds for that role")
    void expandsColours() {
        assertThat(StyleTokens.expand("<body>hello</body>", theme, true)).isEqualTo("<color:#ffffff>hello</color>");
    }

    @Test
    @DisplayName("a tag that is not a token is left for MiniMessage")
    void keepsForeignTags() {
        assertThat(StyleTokens.expand("<b><player></b>", theme, true)).isEqualTo("<b><player></b>");
    }

    @Test
    @DisplayName("a category prefix is the word in bold, then the dim separator")
    void expandsCategoryPrefix() {
        assertThat(StyleTokens.expand("<tag:'HOME'>", theme, true))
                .isEqualTo("<b><color:#38b6ff>ʜᴏᴍᴇ</color></b> <color:#6b7886>▶</color>");
    }

    @Test
    @DisplayName("a category the theme colours differently keeps its own colour")
    void coloursMoneyCategories() {
        assertThat(StyleTokens.expand("<tag:'shop'>", theme, false)).contains("<color:#5be38c>shop</color>");
    }

    @Test
    @DisplayName("a denial reads in the failure colour whichever feature raised it")
    void expandsErrorPrefix() {
        assertThat(StyleTokens.expand("<etag:'ERROR'>", theme, true))
                .isEqualTo("<b><color:#ff6b6b>ᴇʀʀᴏʀ</color></b> <color:#6b7886>▶</color>");
    }

    @Test
    @DisplayName("a header is bold and in the accent colour")
    void expandsHeader() {
        assertThat(StyleTokens.expand("<h:'REWARDS'>", theme, true)).isEqualTo("<b><color:#38b6ff>ʀᴇᴡᴀʀᴅꜱ</color></b>");
    }

    @Test
    @DisplayName("a prefix with no label is a defect, not an empty line")
    void refusesAPrefixWithNoLabel() {
        assertThatThrownBy(() -> StyleTokens.expand("<tag:''> hello", theme, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label");
    }
}
