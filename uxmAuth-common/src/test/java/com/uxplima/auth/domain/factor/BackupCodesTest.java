package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The backup codes of a lost phone: drawn from letters nobody misreads, shown in two halves, typed any way. */
final class BackupCodesTest {

    private final BackupCodes codes = new BackupCodes(8, 10);

    @Test
    @DisplayName("a set is as many codes as the setting, each as long, from the letters and digits nobody misreads")
    void aSetFitsTheSettings() {
        List<String> drawn = codes.draw(new SplittableRandom(4));

        assertThat(drawn).hasSize(8).doesNotHaveDuplicates();
        assertThat(drawn).allSatisfy(code -> assertThat(code).hasSize(10).matches("[" + BackupCodes.ALPHABET + "]+"));
        assertThat(new HashSet<>(codes.draw(new SplittableRandom(5)))).doesNotContainAnyElementsOf(drawn);
    }

    @Test
    @DisplayName("a code is shown in two halves, and read back typed in any case, with or without the dash and spaces")
    void aCodeIsShownAndReadBack() {
        assertThat(BackupCodes.shown("ABCDEFGHJK")).isEqualTo("ABCDE-FGHJK");
        assertThat(BackupCodes.shown("ABCDEFGHJ")).isEqualTo("ABCD-EFGHJ");

        assertThat(codes.read("abcde-fghjk")).contains("ABCDEFGHJK");
        assertThat(codes.read(" ABCDE FGHJK ")).contains("ABCDEFGHJK");
        assertThat(codes.read("ABCDEFGHJ")).isEmpty();
        assertThat(codes.read("ABCDEFGHJ0")).as("0 is never drawn").isEmpty();
        assertThat(codes.read("123456")).isEmpty();
    }

    @Test
    @DisplayName(
            "a count of 1 to 20 and a length of 8 to 16 are taken, anything else is refused with the setting's name")
    void badSettingsAreRefused() {
        assertThatThrownBy(() -> new BackupCodes(0, 10)).hasMessageContaining("backup-codes");
        assertThatThrownBy(() -> new BackupCodes(21, 10)).hasMessageContaining("backup-codes");
        assertThatThrownBy(() -> new BackupCodes(8, 7)).hasMessageContaining("backup-code-length");
        assertThatThrownBy(() -> new BackupCodes(8, 17)).hasMessageContaining("backup-code-length");
        assertThat(new BackupCodes(1, 8).draw(new SplittableRandom(1)))
                .singleElement()
                .asString()
                .hasSize(8);
        assertThat(new BackupCodes(20, 16).draw(new SplittableRandom(1))).hasSize(20);
    }
}
