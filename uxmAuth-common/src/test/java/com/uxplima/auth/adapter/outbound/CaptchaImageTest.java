package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.SplittableRandom;

import com.uxplima.auth.domain.protection.CaptchaPicture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The picture as an image a map takes: dark ink on light paper, pixel for pixel. */
final class CaptchaImageTest {

    @Test
    @DisplayName("every inked pixel is the ink colour and every other the paper colour, on a map-sized square")
    void inkAndPaper() {
        CaptchaPicture picture = CaptchaPicture.draw("K7QXR", new SplittableRandom(4));

        BufferedImage image = CaptchaImage.of(picture);

        assertThat(image.getWidth()).isEqualTo(CaptchaPicture.SIZE);
        assertThat(image.getHeight()).isEqualTo(CaptchaPicture.SIZE);
        int inked = 0;
        for (int y = 0; y < CaptchaPicture.SIZE; y++) {
            for (int x = 0; x < CaptchaPicture.SIZE; x++) {
                int expected = picture.ink(x, y) ? CaptchaImage.INK.getRGB() : CaptchaImage.PAPER.getRGB();
                assertThat(image.getRGB(x, y)).as("pixel %d,%d", x, y).isEqualTo(expected);
                inked += picture.ink(x, y) ? 1 : 0;
            }
        }
        assertThat(inked).isPositive();
        assertThat(CaptchaImage.INK).isNotEqualTo(CaptchaImage.PAPER);
    }
}
