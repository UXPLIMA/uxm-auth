package com.uxplima.auth.adapter.outbound;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.uxmlib.config.HoconConfig;

/**
 * Where another login plugin keeps its accounts and how it writes them, read from a file in {@code imports/}.
 *
 * <p>A source is data, not code: the plugin ships a file for every source it knows, and an operator who meets
 * another one writes a file of their own. Every table and column name must be a plain SQL name, because a name
 * cannot be bound and is put in the statement as it is written.
 *
 * @param key the file's name without {@code .conf}, which {@code import.source} names
 * @param display the source's name as the console prints it
 * @param kind a table, or a YAML file on Paper
 * @param table the table, or for a YAML source its file
 */
public record ImportLayout(
        String key,
        String display,
        Kind kind,
        String table,
        IdStyle idStyle,
        TimeStyle timeStyle,
        LegacyHashes.Style hashStyle,
        Columns columns) {

    /** The folder the layouts live in, beside config.conf. */
    public static final String FOLDER = "imports";

    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]{0,127}");
    private static final Pattern SQL_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    /** Where the accounts are. */
    public enum Kind {
        TABLE,
        YAML,
        /** A table of premium names, read after the accounts to turn the cracked ones it names premium. */
        PREMIUM
    }

    /** How a source writes an id. */
    public enum IdStyle {
        NONE,
        DASHED,
        UNDASHED
    }

    /** How a source writes a date. */
    public enum TimeStyle {
        NONE,
        MILLIS,
        SECONDS,
        TIMESTAMP
    }

    /**
     * The source's columns, each empty when it has none. Either a name or an id is required: a source with no name
     * column has its names looked up by id.
     *
     * @param premiumId a column holding the Mojang id of a paid account
     * @param premiumFlag a column saying the account's own id is Mojang's
     * @param salt a column holding the salt, for a source that keeps it apart
     * @param algorithm a column naming the hash's algorithm, for a source that keeps it apart
     */
    public record Columns(
            String name,
            String id,
            String premiumId,
            String premiumFlag,
            String password,
            String salt,
            String algorithm,
            String totp,
            String registered,
            String lastLogin,
            String address) {

        /** Every column that is set, in this order. */
        public List<String> named() {
            return Stream.of(
                            name,
                            id,
                            premiumId,
                            premiumFlag,
                            password,
                            salt,
                            algorithm,
                            totp,
                            registered,
                            lastLogin,
                            address)
                    .filter(column -> !column.isEmpty())
                    .toList();
        }
    }

    public ImportLayout {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(display, "display");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(idStyle, "idStyle");
        Objects.requireNonNull(timeStyle, "timeStyle");
        Objects.requireNonNull(hashStyle, "hashStyle");
        Objects.requireNonNull(columns, "columns");
    }

    /** The layout this file describes, refused by the key that is wrong. */
    public static ImportLayout read(String key, HoconConfig config) {
        Objects.requireNonNull(config, "config");
        Kind kind = choice(Kind.class, config, "kind", "table");
        String table = config.getString("table", "").trim();
        if (kind != Kind.YAML && !table.isEmpty() && !SQL_NAME.matcher(table).matches()) {
            throw new IllegalArgumentException(
                    "table is " + table + ", and a table is letters, digits and underscores");
        }
        Columns columns = columns(config);
        if (kind == Kind.PREMIUM
                && columns.premiumId().isEmpty()
                && columns.premiumFlag().isEmpty()) {
            throw new IllegalArgumentException("a premium list names its premium-flag or premium-id column");
        }
        return new ImportLayout(
                Objects.requireNonNull(key, "key"),
                config.getString("display", key),
                kind,
                table,
                choice(IdStyle.class, config, "id-style", "none"),
                choice(TimeStyle.class, config, "time-style", "none"),
                hashStyle(config.getString("hash-style", "as-is")),
                columns);
    }

    /** This layout reading another table, for a source the operator renamed. */
    public ImportLayout onTable(String other) {
        if (!SQL_NAME.matcher(Objects.requireNonNull(other, "other")).matches()) {
            throw new IllegalArgumentException(
                    "import.table is " + other + ", and a table is letters, digits and underscores");
        }
        return new ImportLayout(key, display, kind, other, idStyle, timeStyle, hashStyle, columns);
    }

    private static Columns columns(HoconConfig config) {
        Columns columns = new Columns(
                column(config, "name"),
                column(config, "id"),
                column(config, "premium-id"),
                column(config, "premium-flag"),
                column(config, "password"),
                column(config, "salt"),
                column(config, "algorithm"),
                column(config, "totp"),
                column(config, "registered"),
                column(config, "last-login"),
                column(config, "address"));
        if (columns.name().isEmpty() && columns.id().isEmpty()) {
            throw new IllegalArgumentException(
                    "columns.name is empty and so is columns.id: one of them names an account");
        }
        Set<String> seen = new HashSet<>();
        for (String column : columns.named()) {
            if (!seen.add(column.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("columns names " + column + " twice");
            }
        }
        return columns;
    }

    private static String column(HoconConfig config, String name) {
        String written = config.getString("columns." + name, "").trim();
        if (!written.isEmpty() && !SQL_NAME.matcher(written).matches()) {
            throw new IllegalArgumentException(
                    "columns." + name + " is " + written + ", and a column is letters, digits and underscores");
        }
        return written;
    }

    private static LegacyHashes.Style hashStyle(String written) {
        try {
            return LegacyHashes.Style.of(written);
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException(unknown.getMessage(), unknown);
        }
    }

    private static <E extends Enum<E>> E choice(Class<E> type, HoconConfig config, String path, String fallback) {
        String written = config.getString(path, fallback).trim();
        for (E value : type.getEnumConstants()) {
            if (value.name().replace('_', '-').equalsIgnoreCase(written)) {
                return value;
            }
        }
        throw new IllegalArgumentException(path + " is " + written + ", and it is one of "
                + Arrays.stream(type.getEnumConstants())
                        .map(value -> value.name().toLowerCase(Locale.ROOT).replace('_', '-'))
                        .collect(Collectors.joining(", ")));
    }

    /**
     * Every layout in the folder, keyed by its file's name. The ones the plugin ships are written there first when
     * they are missing, so an operator reads and changes them where they are.
     */
    public static Map<String, ImportLayout> shipped(Path folder) {
        Objects.requireNonNull(folder, "folder");
        try {
            Files.createDirectories(folder);
            for (String file : shippedFiles()) {
                Path target = folder.resolve(file);
                if (Files.notExists(target)) {
                    try (InputStream in = resource(FOLDER + "/" + file)) {
                        Files.copy(in, target);
                    }
                }
            }
            Map<String, ImportLayout> layouts = new TreeMap<>();
            try (Stream<Path> files = Files.list(folder)) {
                for (Path file :
                        files.filter(path -> path.toString().endsWith(".conf")).toList()) {
                    String key = file.getFileName().toString().replaceFirst("\\.conf$", "");
                    if (NAME.matcher(key).matches()) {
                        layouts.put(key, read(key, HoconConfig.load(file)));
                    }
                }
            }
            return layouts;
        } catch (IOException failed) {
            throw new UncheckedIOException("the import layouts in " + folder + " could not be written", failed);
        }
    }

    /** The files the jar carries, as its index lists them. */
    private static List<String> shippedFiles() throws IOException {
        List<String> files = new ArrayList<>();
        try (InputStream in = resource(FOLDER + "/index.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n", -1)) {
                if (!line.isBlank()) {
                    files.add(line.trim());
                }
            }
        }
        return files;
    }

    private static InputStream resource(String path) throws IOException {
        InputStream in = ImportLayout.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IOException("the jar has no " + path);
        }
        return in;
    }
}
