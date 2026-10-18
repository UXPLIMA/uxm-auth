package com.uxplima.auth.domain.factor;

import java.util.Objects;

import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.Encoder;

/** The modules of a QR code, without the quiet zone around them, which whoever draws it adds. */
public final class QrMatrix {

    private final boolean[][] modules;

    private QrMatrix(boolean[][] modules) {
        this.modules = modules;
    }

    /** The code of this text, at the middle error correction level a screen photographed by a phone needs. */
    public static QrMatrix of(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isEmpty()) {
            throw new IllegalArgumentException("A QR code of an empty text says nothing");
        }
        ByteMatrix matrix;
        try {
            matrix = Encoder.encode(text, ErrorCorrectionLevel.M).getMatrix();
        } catch (WriterException tooLong) {
            throw new IllegalArgumentException("The text is too long for any QR code", tooLong);
        }
        boolean[][] modules = new boolean[matrix.getWidth()][matrix.getHeight()];
        for (int x = 0; x < modules.length; x++) {
            for (int y = 0; y < modules[x].length; y++) {
                modules[x][y] = matrix.get(x, y) == 1;
            }
        }
        return new QrMatrix(modules);
    }

    /** How many modules a side has. */
    public int size() {
        return modules.length;
    }

    /** Whether the module in this column and row is dark. */
    public boolean dark(int x, int y) {
        Objects.checkIndex(x, modules.length);
        Objects.checkIndex(y, modules.length);
        return modules[x][y];
    }
}
