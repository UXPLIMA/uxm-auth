package com.uxplima.auth.adapter.outbound;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

import com.uxplima.auth.domain.protection.CaptchaPicture;

/** A captcha picture as the image a map shows: dark ink on light paper, one pixel for one. */
public final class CaptchaImage {

    /** The colour of the code and its noise; both sit on the map palette, so nothing is mixed away. */
    public static final Color INK = new Color(25, 25, 25);

    public static final Color PAPER = new Color(247, 233, 163);

    private CaptchaImage() {}

    public static BufferedImage of(CaptchaPicture picture) {
        Objects.requireNonNull(picture, "picture");
        BufferedImage image = new BufferedImage(CaptchaPicture.SIZE, CaptchaPicture.SIZE, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < CaptchaPicture.SIZE; y++) {
            for (int x = 0; x < CaptchaPicture.SIZE; x++) {
                image.setRGB(x, y, (picture.ink(x, y) ? INK : PAPER).getRGB());
            }
        }
        return image;
    }
}
