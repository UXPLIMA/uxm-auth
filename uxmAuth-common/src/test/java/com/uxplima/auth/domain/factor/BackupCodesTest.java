package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class BackupCodesTest {

    private final BackupCodes codes = new BackupCodes(10, 10);

    @Test
    void theRequestedNumberOfDistinctCodesIsDrawnFromTheUnambiguousAlphabet() {
        List<String> drawn = codes.draw(new Random(7));

        assertThat(drawn).hasSize(10).doesNotHaveDuplicates();
        assertThat(String.join("", drawn)).doesNotContain("0", "O", "1", "I", "L");
    }

    @Test
    void aCodeIsReadAsShownOrTypedLoosely() {
        String code = codes.draw(new Random(7)).getFirst();

        assertThat(codes.read(BackupCodes.shown(code))).contains(code);
        assertThat(codes.read(code.toLowerCase(java.util.Locale.ROOT))).contains(code);
        assertThat(BackupCodes.shown(code)).hasSize(11).contains("-");
    }

    @Test
    void aCodeOfTheWrongLengthOrAlphabetIsNotACode() {
        assertThat(codes.read("ABCDEFGHJ")).isEmpty();
        assertThat(codes.read("ABCDEFGHJ0")).isEmpty();
    }

    @Test
    void theConfiguredCountAndLengthAreBounded() {
        assertThatThrownBy(() -> new BackupCodes(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BackupCodes(10, 7)).isInstanceOf(IllegalArgumentException.class);
    }
}
