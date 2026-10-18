package com.uxplima.auth.testing;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Writes a small country file in the MaxMind DB format for a test, so no binary is kept in the repository: an
 * IPv4 tree of 24-bit records, a data section of {@code {country: {iso_code}}} maps, and the metadata a reader
 * asks for. Only what the country rule reads is written.
 */
public final class CountryDatabase {

    private static final int RECORD_BYTES = 3;
    private static final byte[] METADATA_MARKER = {
        (byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 'M', 'a', 'x', 'M', 'i', 'n', 'd', '.', 'c', 'o', 'm'
    };

    private static final class Node {
        @Nullable Object left;

        @Nullable Object right;
    }

    private record Leaf(int offset) {}

    private CountryDatabase() {}

    /** Writes the ranges, each an IPv4 range with its prefix mapped to a country code, to this file. */
    public static Path write(Path file, Map<String, String> ranges) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        Map<String, Integer> offsets = new HashMap<>();
        Node root = new Node();
        for (Map.Entry<String, String> range : ranges.entrySet()) {
            int offset = offsets.computeIfAbsent(range.getValue(), code -> {
                int at = data.size();
                writeCountry(data, code);
                return at;
            });
            insert(root, range.getKey(), new Leaf(offset));
        }
        List<Node> nodes = numbered(root);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Node node : nodes) {
            writeRecord(out, value(node.left, nodes), RECORD_BYTES);
            writeRecord(out, value(node.right, nodes), RECORD_BYTES);
        }
        out.write(new byte[16]);
        out.write(data.toByteArray());
        out.write(METADATA_MARKER);
        writeMetadata(out, nodes.size());
        return Files.write(file, out.toByteArray());
    }

    private static void insert(Node root, String range, Leaf leaf) throws IOException {
        int slash = range.indexOf('/');
        byte[] address = InetAddress.ofLiteral(range.substring(0, slash)).getAddress();
        int prefix = Integer.parseInt(range.substring(slash + 1));
        Node node = root;
        for (int bit = 0; bit < prefix; bit++) {
            boolean one = ((address[bit / 8] >> (7 - bit % 8)) & 1) == 1;
            if (bit == prefix - 1) {
                if (one) {
                    node.right = leaf;
                } else {
                    node.left = leaf;
                }
                return;
            }
            Object next = one ? node.right : node.left;
            Node child = next instanceof Node existing ? existing : new Node();
            if (one) {
                node.right = child;
            } else {
                node.left = child;
            }
            node = child;
        }
    }

    private static List<Node> numbered(Node root) {
        List<Node> nodes = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            Node node = queue.removeFirst();
            nodes.add(node);
            for (Object child : new Object[] {node.left, node.right}) {
                if (child instanceof Node next) {
                    queue.addLast(next);
                }
            }
        }
        return nodes;
    }

    private static long value(@Nullable Object child, List<Node> nodes) {
        return switch (child) {
            case null -> nodes.size();
            case Node node -> nodes.indexOf(node);
            case Leaf leaf -> nodes.size() + 16L + leaf.offset();
            default -> throw new IllegalStateException("A record is a node, a leaf or nothing");
        };
    }

    private static void writeCountry(ByteArrayOutputStream out, String code) {
        control(out, 7, 1);
        string(out, "country");
        control(out, 7, 1);
        string(out, "iso_code");
        string(out, code);
    }

    private static void writeMetadata(ByteArrayOutputStream out, int nodeCount) {
        Map<String, Runnable> entries = new LinkedHashMap<>();
        entries.put("binary_format_major_version", () -> unsigned(out, 5, 2));
        entries.put("binary_format_minor_version", () -> unsigned(out, 5, 0));
        entries.put("build_epoch", () -> extended(out, 9, 1_760_000_000L));
        entries.put("database_type", () -> string(out, "GeoLite2-Country"));
        entries.put("description", () -> {
            control(out, 7, 1);
            string(out, "en");
            string(out, "uxmAuth test countries");
        });
        entries.put("ip_version", () -> unsigned(out, 5, 4));
        entries.put("languages", () -> {
            out.write(1);
            out.write(11 - 7);
            string(out, "en");
        });
        entries.put("node_count", () -> unsigned(out, 6, nodeCount));
        entries.put("record_size", () -> unsigned(out, 5, RECORD_BYTES * 8));
        control(out, 7, entries.size());
        entries.forEach((key, value) -> {
            string(out, key);
            value.run();
        });
    }

    private static void control(ByteArrayOutputStream out, int type, int size) {
        out.write((type << 5) | size);
    }

    private static void string(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        control(out, 2, bytes.length);
        out.writeBytes(bytes);
    }

    private static void unsigned(ByteArrayOutputStream out, int type, long value) {
        byte[] bytes = minimal(value);
        control(out, type, bytes.length);
        out.writeBytes(bytes);
    }

    private static void extended(ByteArrayOutputStream out, int type, long value) {
        byte[] bytes = minimal(value);
        out.write(bytes.length);
        out.write(type - 7);
        out.writeBytes(bytes);
    }

    private static byte[] minimal(long value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        boolean started = false;
        for (int shift = 56; shift >= 0; shift -= 8) {
            int part = (int) ((value >> shift) & 0xff);
            if (part != 0 || started) {
                bytes.write(part);
                started = true;
            }
        }
        return bytes.toByteArray();
    }

    private static void writeRecord(ByteArrayOutputStream out, long value, int bytes) {
        for (int shift = (bytes - 1) * 8; shift >= 0; shift -= 8) {
            out.write((int) ((value >> shift) & 0xff));
        }
    }
}
