package com.uxplima.auth.domain.factor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The modules of a QR code, without the quiet zone around them. */
final class QrMatrixTest {

    @Test
    @DisplayName("a short text is a version 1 code of 21 modules, with its finder patterns in three corners")
    void aShortTextIsVersionOne() {
        QrMatrix matrix = QrMatrix.of("uxm");

        assertThat(matrix.size()).isEqualTo(21);
        for (int corner : new int[] {0, 6}) {
            assertThat(matrix.dark(corner, corner)).isTrue();
            assertThat(matrix.dark(20 - corner, corner)).isTrue();
            assertThat(matrix.dark(corner, 20 - corner)).isTrue();
        }
        assertThat(matrix.dark(7, 7)).isFalse();
        assertThat(matrix.dark(7, 0)).isFalse();
    }

    @Test
    @DisplayName("a longer text takes a larger code, and nothing outside the code is a module")
    void aLongerTextTakesALargerCode() {
        QrMatrix matrix = QrMatrix.of("otpauth://totp/Uxplima:Steve?secret=" + "A".repeat(32)
                + "&issuer=Uxplima&algorithm=SHA1&digits=6&period=30");

        assertThat(matrix.size()).isGreaterThan(21).isLessThanOrEqualTo(57);
        assertThatThrownBy(() -> matrix.dark(matrix.size(), 0)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> matrix.dark(0, -1)).isInstanceOf(IndexOutOfBoundsException.class);
        assertThatThrownBy(() -> QrMatrix.of("")).hasMessageContaining("empty");
    }
}
