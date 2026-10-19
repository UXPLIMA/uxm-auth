package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.uxplima.auth.application.migrate.MoveDatabase;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Every table of ours goes across with every row and every value, between the databases this jar speaks. */
final class TableCopyTest {

    private static final WebsiteTable.Columns COLUMNS =
            new WebsiteTable.Columns("username", "realname", "password", "ip", "lastlogin", "regdate", "regip");
    private static final WebsiteTable OFF = new WebsiteTable(false, "authme", WebsiteTable.Hash.SHA256, COLUMNS);
    private static final WebsiteTable ON = new WebsiteTable(true, "authme", WebsiteTable.Hash.SHA256, COLUMNS);

    @TempDir
    Path folder;

    private PluginSettings.Storage storage(String engine, String name) {
        return engine.equals("h2")
                ? new PluginSettings.Storage("jdbc:h2:" + folder.resolve(name).toAbsolutePath(), "sa", "", 2)
                : new PluginSettings.Storage("jdbc:sqlite:" + folder.resolve(name + ".db"), "", "", 2);
    }

    static Stream<Arguments> engines() {
        return Stream.of(Arguments.of("sqlite", "h2"), Arguments.of("h2", "sqlite"), Arguments.of("sqlite", "sqlite"));
    }

    @ParameterizedTest(name = "{0} to {1}")
    @MethodSource("engines")
    @DisplayName("every table is copied with every row and every value, and the counts agree")
    void everyTableIsCopied(String from, String to) throws SQLException {
        PluginSettings.Storage target = storage(to, "target");
        try (Database source = AuthDatabase.open(storage(from, "source"), "source")) {
            seed(source, TableCopy.TABLES, 2);

            MoveDatabase.Moved moved;
            try (TableCopy copy = TableCopy.open(source, target, OFF, "test")) {
                assertThat(copy.targetRows().values()).containsOnly(0L);
                moved = new MoveDatabase(() -> 0, () -> Optional.of(copy)).move();
            }

            assertThat(moved).isInstanceOf(MoveDatabase.Moved.Done.class);
            assertThat(((MoveDatabase.Moved.Done) moved).rows()).containsOnlyKeys(TableCopy.TABLES);
            assertThat(((MoveDatabase.Moved.Done) moved).rows().values()).containsOnly(2L);
            try (Database moved2 = AuthDatabase.open(target, "check")) {
                for (String table : TableCopy.TABLES) {
                    assertThat(dump(moved2, table)).as(table).isEqualTo(dump(source, table));
                }
            }
        }
    }

    @Test
    @DisplayName("a second move onto the target it filled is refused, and writes nothing")
    void aFullTargetIsRefused() throws SQLException {
        PluginSettings.Storage target = storage("sqlite", "target");
        try (Database source = AuthDatabase.open(storage("sqlite", "source"), "source")) {
            seed(source, TableCopy.TABLES, 1);
            try (TableCopy copy = TableCopy.open(source, target, OFF, "test")) {
                assertThat(new MoveDatabase(() -> 0, () -> Optional.of(copy)).move())
                        .isInstanceOf(MoveDatabase.Moved.Done.class);
            }
            seed(source, List.of("uxmauth_account"), 3);

            try (TableCopy copy = TableCopy.open(source, target, OFF, "test")) {
                assertThat(new MoveDatabase(() -> 0, () -> Optional.of(copy)).move())
                        .isEqualTo(new MoveDatabase.Moved.Refused(MoveDatabase.Refusal.TARGET_HOLDS_ROWS));
            }
            try (Database moved = AuthDatabase.open(target, "check")) {
                assertThat(dump(moved, "uxmauth_account")).hasSize(1);
            }
        }
    }

    @Test
    @DisplayName("the tables copied are every table the migrations make, but their own history")
    void everyMigratedTableIsNamed() throws SQLException {
        try (Database database = AuthDatabase.open(storage("h2", "schema"), "schema")) {
            assertThat(new TreeSet<>(tablesOf(database))).isEqualTo(new TreeSet<>(TableCopy.TABLES));
        }
    }

    @Test
    @DisplayName(
            "the website table moves when it is on, with its ids, and the next row the target makes gets a new one")
    void theWebsiteTableMovesWhenOn() throws SQLException {
        PluginSettings.Storage target = storage("sqlite", "target");
        try (Database source = AuthDatabase.open(storage("sqlite", "source"), "source")) {
            new WebsiteRows(source, ON).prepare();
            insertWebsiteRow(source, 7, "steve");
            insertWebsiteRow(source, 9, "alex");
            try (TableCopy copy = TableCopy.open(source, target, ON, "test")) {
                assertThat(copy.copy()).containsEntry("authme", 2L);
            }
            try (Database moved = AuthDatabase.open(target, "check")) {
                assertThat(dump(moved, "authme")).isEqualTo(dump(source, "authme"));
                insertWebsiteRow(moved, null, "notch");
                assertThat(dump(moved, "authme")).anyMatch(row -> row.contains("notch") && row.contains("id=10"));
            }
        }
    }

    @Test
    @DisplayName("into H2 the website's rows take new numbers, so the next row the target makes collides with none")
    void theWebsiteTableIsRenumberedIntoH2() throws SQLException {
        PluginSettings.Storage target = storage("h2", "target");
        try (Database source = AuthDatabase.open(storage("sqlite", "source"), "source")) {
            new WebsiteRows(source, ON).prepare();
            insertWebsiteRow(source, 1, "steve");
            insertWebsiteRow(source, 2, "alex");
            try (TableCopy copy = TableCopy.open(source, target, ON, "test")) {
                assertThat(copy.copy()).containsEntry("authme", 2L);
            }
            try (Database moved = AuthDatabase.open(target, "check")) {
                insertWebsiteRow(moved, null, "notch");
                assertThat(dump(moved, "authme"))
                        .hasSize(3)
                        .anyMatch(row -> row.contains("username=steve"))
                        .anyMatch(row -> row.contains("username=notch"));
            }
        }
    }

    @Test
    @DisplayName("the website table stays where it is when it is off")
    void theWebsiteTableStaysWhenOff() throws SQLException {
        PluginSettings.Storage target = storage("sqlite", "target");
        try (Database source = AuthDatabase.open(storage("sqlite", "source"), "source")) {
            new WebsiteRows(source, ON).prepare();
            insertWebsiteRow(source, 7, "steve");
            try (TableCopy copy = TableCopy.open(source, target, OFF, "test")) {
                assertThat(copy.copy()).doesNotContainKey("authme");
            }
            try (Database moved = AuthDatabase.open(target, "check")) {
                assertThat(tablesOf(moved)).doesNotContain("authme");
            }
        }
    }

    /** The tables of ours a database holds, by the names the migrations gave them. */
    private static List<String> tablesOf(Database database) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (Connection connection = database.connection();
                ResultSet found = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
            while (found.next()) {
                String name = found.getString("TABLE_NAME").toLowerCase(Locale.ROOT);
                if (!name.equals(AuthSchema.HISTORY) && (name.startsWith("uxmauth_") || name.equals("authme"))) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }

    private static void insertWebsiteRow(Database database, @Nullable Integer id, String name) throws SQLException {
        String sql = id == null
                ? "INSERT INTO authme (username, realname, password, regdate) VALUES (?, ?, 'x', 0)"
                : "INSERT INTO authme (id, username, realname, password, regdate) VALUES (?, ?, ?, 'x', 0)";
        try (Connection connection = database.connection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            int at = 1;
            if (id != null) {
                statement.setInt(at++, id);
            }
            statement.setString(at++, name);
            statement.setString(at, name);
            statement.executeUpdate();
        }
    }

    /** Fills each table with rows whose every column holds a value of its type, distinct per row. */
    private static void seed(Database database, List<String> tables, int rows) throws SQLException {
        try (Connection connection = database.connection()) {
            for (String table : tables) {
                List<int[]> types = new ArrayList<>();
                List<String> names = new ArrayList<>();
                try (Statement statement = connection.createStatement();
                        ResultSet empty = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
                    ResultSetMetaData meta = empty.getMetaData();
                    for (int at = 1; at <= meta.getColumnCount(); at++) {
                        names.add(meta.getColumnName(at));
                        types.add(new int[] {meta.getColumnType(at), Math.max(meta.getPrecision(at), 0)});
                    }
                }
                long start = count(connection, table);
                for (long row = start; row < start + rows; row++) {
                    insert(connection, table, names, types, row);
                }
            }
        }
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet counted = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            counted.next();
            return counted.getLong(1);
        }
    }

    private static void insert(Connection connection, String table, List<String> names, List<int[]> types, long row)
            throws SQLException {
        String marks = String.join(", ", names.stream().map(name -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (" + String.join(", ", names) + ") VALUES (" + marks + ")")) {
            for (int at = 0; at < names.size(); at++) {
                int type = types.get(at)[0];
                switch (type) {
                    case Types.BOOLEAN, Types.BIT -> statement.setBoolean(at + 1, row % 2 == 1);
                    case Types.TINYINT, Types.SMALLINT -> statement.setLong(at + 1, row % 2);
                    case Types.INTEGER -> statement.setLong(at + 1, row * 7 + at);
                    case Types.BIGINT -> statement.setLong(at + 1, 1_760_000_000_000L + row * 7 + at);
                    default -> statement.setString(at + 1, text(names.get(at), types.get(at)[1], row));
                }
            }
            statement.executeUpdate();
        }
    }

    private static String text(String column, int size, long row) {
        String value = column.toLowerCase(Locale.ROOT).charAt(0) + "-" + row + "-" + column.length() + "-ü";
        return size > 0 && value.length() > size ? value.substring(value.length() - size) : value;
    }

    /** Every row of a table as text, column by column, the same whatever database answers. */
    private static List<String> dump(Database database, String table) throws SQLException {
        List<String> rows = new ArrayList<>();
        try (Connection connection = database.connection();
                Statement statement = connection.createStatement();
                ResultSet found = statement.executeQuery("SELECT * FROM " + table)) {
            ResultSetMetaData meta = found.getMetaData();
            while (found.next()) {
                Map<String, String> row = new TreeMap<>();
                for (int at = 1; at <= meta.getColumnCount(); at++) {
                    row.put(meta.getColumnName(at).toLowerCase(Locale.ROOT), valueOf(found, meta, at));
                }
                rows.add(row.toString());
            }
        }
        rows.sort(null);
        return rows;
    }

    private static String valueOf(ResultSet found, ResultSetMetaData meta, int at) throws SQLException {
        String value =
                switch (meta.getColumnType(at)) {
                    case Types.BOOLEAN, Types.BIT -> String.valueOf(found.getBoolean(at));
                    case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT ->
                        String.valueOf(found.getLong(at));
                    default -> found.getString(at);
                };
        return found.wasNull() ? "null" : value;
    }
}
