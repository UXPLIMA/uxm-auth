package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.uxplima.auth.domain.factor.QrMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The QR code drawn on a map: a phone reads the link back from the picture. */
final class QrImageTest {

    private static final String LINK = "otpauth://totp/Uxplima%20Network:Steve?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
            + "&issuer=Uxplima%20Network&algorithm=SHA1&digits=6&period=30";

    @Test
    @DisplayName("the picture fills a map, and a reader takes the link back from it")
    void aReaderTakesTheLinkBack() throws Exception {
        BufferedImage picture = QrImage.of(QrMatrix.of(LINK));

        assertThat(picture.getWidth()).isEqualTo(128);
        assertThat(picture.getHeight()).isEqualTo(128);
        int[] pixels = picture.getRGB(0, 0, 128, 128, null, 0, 128);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(128, 128, pixels)));

        assertThat(new QRCodeReader().decode(bitmap).getText()).isEqualTo(LINK);
    }

    @ParameterizedTest
    @ValueSource(ints = {20, 100, 140})
    @DisplayName("the code sits in the middle with a quiet zone of at least four modules on every side")
    void aQuietZoneSurroundsTheCode(int length) {
        BufferedImage picture = QrImage.of(QrMatrix.of("x".repeat(length)));
        int first = 128;
        int last = -1;
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                if (picture.getRGB(x, y) == QrImage.DARK.getRGB()) {
                    first = Math.min(first, Math.min(x, y));
                    last = Math.max(last, Math.max(x, y));
                }
            }
        }
        // The finder pattern in the top left corner is seven modules wide, so its first row gives the module.
        int run = 0;
        while (picture.getRGB(first + run, first) == QrImage.DARK.getRGB()) {
            run++;
        }
        int module = run / 7;

        assertThat(run % 7).isZero();
        assertThat(module).isGreaterThanOrEqualTo(2);
        assertThat(first).isGreaterThanOrEqualTo(4 * module);
        assertThat(127 - last).isGreaterThanOrEqualTo(4 * module);
        assertThat(Math.abs(first - (127 - last))).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("a text too long for a map is refused with the settings that make it long")
    void aTextTooLongIsRefused() {
        QrMatrix huge = QrMatrix.of("x".repeat(1200));

        assertThatThrownBy(() -> QrImage.of(huge))
                .hasMessageContaining("issuer")
                .hasMessageContaining("label");
    }
}
