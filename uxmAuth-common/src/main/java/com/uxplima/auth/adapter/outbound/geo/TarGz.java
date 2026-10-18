package com.uxplima.auth.adapter.outbound.geo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads one file out of a tar stream, the way MaxMind packs its databases. The entry's own path is never used
 * to write: only its name is compared, and the content goes to the target the caller chose.
 */
final class TarGz {

    private static final int BLOCK = 512;
    private static final long LARGEST = 256L * 1024 * 1024;

    private TarGz() {}

    /** Copies the first regular entry whose name ends with {@code name} to the target, and says whether one was. */
    static boolean extract(InputStream tar, String name, Path target) throws IOException {
        byte[] header = new byte[BLOCK];
        while (tar.readNBytes(header, 0, BLOCK) == BLOCK && header[0] != 0) {
            String entry = text(header, 0, 100);
            long size = size(text(header, 124, 12).strip());
            if (size < 0 || size > LARGEST) {
                throw new IOException("A tar entry of " + size + " bytes is larger than a country file grows");
            }
            boolean regular = header[156] == '0' || header[156] == 0;
            if (regular && (entry.equals(name) || entry.endsWith("/" + name))) {
                try (OutputStream out = Files.newOutputStream(target)) {
                    copy(tar, out, size);
                }
                return true;
            }
            tar.skipNBytes(padded(size));
        }
        return false;
    }

    private static void copy(InputStream from, OutputStream to, long size) throws IOException {
        byte[] buffer = new byte[8192];
        long left = size;
        while (left > 0) {
            int read = from.read(buffer, 0, (int) Math.min(buffer.length, left));
            if (read < 0) {
                throw new IOException("The tar stream ended inside an entry");
            }
            to.write(buffer, 0, read);
            left -= read;
        }
    }

    private static long size(String octal) throws IOException {
        try {
            return octal.isEmpty() ? 0 : Long.parseLong(octal, 8);
        } catch (NumberFormatException broken) {
            throw new IOException("A tar entry names no size it can be read by", broken);
        }
    }

    private static long padded(long size) {
        return (size + BLOCK - 1) / BLOCK * BLOCK;
    }

    private static String text(byte[] header, int from, int length) {
        int end = from;
        while (end < from + length && header[end] != 0) {
            end++;
        }
        return new String(header, from, end - from, StandardCharsets.US_ASCII);
    }
}
