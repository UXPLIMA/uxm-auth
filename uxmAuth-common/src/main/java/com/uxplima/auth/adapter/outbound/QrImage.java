package com.uxplima.auth.adapter.outbound;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Objects;

import com.uxplima.auth.domain.factor.QrMatrix;

/** A QR code drawn on the 128 pixels of a map, as large as it fits with its quiet zone around it. */
public final class QrImage {

    /** The colours of the code, the darkest and the lightest a map shows. */
    public static final Color DARK = Color.BLACK;

    public static final Color LIGHT = Color.WHITE;

    private static final int SIDE = 128;

    /** The quiet zone the QR standard asks around a code, in modules. */
    private static final int QUIET = 4;

    private QrImage() {}

    /** The picture of this code, refused when the code has more modules than a map can show. */
    public static BufferedImage of(QrMatrix matrix) {
        Objects.requireNonNull(matrix, "matrix");
        int scale = SIDE / (matrix.size() + 2 * QUIET);
        if (scale < 1) {
            throw new IllegalArgumentException(
                    "The link is too long for a map; shorten second-factor.issuer" + " or second-factor.label");
        }
        int edge = (SIDE - matrix.size() * scale) / 2;
        BufferedImage image = new BufferedImage(SIDE, SIDE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < SIDE; x++) {
            for (int y = 0; y < SIDE; y++) {
                image.setRGB(x, y, (dark(matrix, x - edge, y - edge, scale) ? DARK : LIGHT).getRGB());
            }
        }
        return image;
    }

    private static boolean dark(QrMatrix matrix, int x, int y, int scale) {
        int column = Math.floorDiv(x, scale);
        int row = Math.floorDiv(y, scale);
        return column >= 0 && row >= 0 && column < matrix.size() && row < matrix.size() && matrix.dark(column, row);
    }
}
