package com.uxplima.template.adapter.outbound.style;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class TypographyTest {

    @Test
    @DisplayName("letters become small capitals and everything else is left alone")
    void convertsLetters() {
        assertThat(Typography.apply("Page 3 of 50", true)).isEqualTo("ᴘᴀɢᴇ 3 ᴏꜰ 50");
    }

    @Test
    @DisplayName("a tag and a placeholder are copied through untouched")
    void keepsTags() {
        assertThat(Typography.apply("<body>You are <value><player></value>", true))
                .isEqualTo("<body>ʏᴏᴜ ᴀʀᴇ <value><player></value>");
    }

    @Test
    @DisplayName("text inside plain markers keeps ordinary letters, and the markers are removed")
    void keepsPlainText() {
        assertThat(Typography.apply("version <plain>1.2.0-beta</plain> here", true))
                .isEqualTo("ᴠᴇʀꜱɪᴏɴ 1.2.0-beta ʜᴇʀᴇ");
    }

    @Test
    @DisplayName("a language without small capitals keeps its own letters and still loses the markers")
    void leavesLettersWhenSwitchedOff() {
        assertThat(Typography.apply("Etiketin <plain>/tag</plain> ile açık", false))
                .isEqualTo("Etiketin /tag ile açık");
    }

    @Test
    @DisplayName("a bracket inside a quoted argument does not end the tag")
    void readsQuotedArguments() {
        assertThat(Typography.apply("<hover:show_text:'a > b'>go", true)).isEqualTo("<hover:show_text:'a > b'>ɢᴏ");
    }
}
