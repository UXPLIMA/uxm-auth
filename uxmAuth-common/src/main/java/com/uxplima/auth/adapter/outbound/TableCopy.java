package com.uxplima.auth.adapter.outbound;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import com.uxplima.auth.application.port.DatabaseCopy;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;
import com.uxplima.uxmlib.storage.sql.InsertBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Copies the plugin's tables from the database the accounts live in to another one this jar speaks.
 *
 * <p>The target is opened the way every start opens a database, so it has the same tables, made by the same
 * migrations. Each value is read by the kind its source column holds and written by the kind its target column
 * takes, because a flag is a number in SQLite and a boolean in PostgreSQL. Rows go across in batches, each batch
 * bound and committed, never written into the statement.
 */
public final class TableCopy implements DatabaseCopy {

    /** Every table the migrations make, but their own history, which each database keeps for itself. */
    static final List<String> TABLES = List.of(
            "uxmauth_account",
            "uxmauth_meta",
            "uxmauth_rename",
            "uxmauth_dispute",
            "uxmauth_last_server",
            "uxmauth_session",
            "uxmauth_session_choice",
            "uxmauth_address",
            "uxmauth_factor",
            "uxmauth_backup_code",
            "uxmauth_email",
            "uxmauth_mail_code",
            "uxmauth_login",
            "uxmauth_website_link");

    /** The rows sent in one batch. */
    private static final int BATCH = 500;

    /** The column a website's own table numbers its rows by. */
    private static final String WEBSITE_ID = "id";

    /** How a column holds its value. */
    private enum Kind {
        FLAG,
        NUMBER,
        TEXT
    }

    private final Database source;
    private final Database target;
    private final List<String> tables;
    private final @Nullable String website;

    private TableCopy(Database source, Database target, List<String> tables, @Nullable String website) {
        this.source = source;
        this.target = target;
        this.tables = tables;
        this.website = website;
    }

    /**
     * Opens the target, brings its tables up to date, and makes the website table there when it is on.
     *
     * @param poolName the name the target's connections are pooled under
     */
    public static TableCopy open(Database source, PluginSettings.Storage to, WebsiteTable website, String poolName) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(website, "website");
        Database target = AuthDatabase.open(Objects.requireNonNull(to, "to"), poolName + "-move");
        if (!website.enabled()) {
            return new TableCopy(source, target, TABLES, null);
        }
        try {
            new WebsiteRows(target, website).prepare();
        } catch (RuntimeException failed) {
            target.close();
            throw failed;
        }
        List<String> all =
                Stream.concat(TABLES.stream(), Stream.of(website.table())).toList();
        return new TableCopy(source, target, all, website.table());
    }

    @Override
    public Map<String, Long> sourceRows() {
        return counts(source);
    }

    @Override
    public Map<String, Long> targetRows() {
        return counts(target);
    }

    @Override
    public Map<String, Long> copy() {
        Map<String, Long> written = new LinkedHashMap<>();
        for (String table : tables) {
            try {
                written.put(table, copyTable(table));
            } catch (SQLException failed) {
                throw new StorageException(
                        "the table " + table + " could not be copied: " + failed.getMessage(), failed);
            }
        }
        return written;
    }

    @Override
    public void close() {
        target.close();
    }

    private Map<String, Long> counts(Database database) {
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection connection = database.connection();
                Statement statement = connection.createStatement()) {
            for (String table : tables) {
                try (ResultSet counted = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    counts.put(table, counted.next() ? counted.getLong(1) : 0L);
                }
            }
        } catch (SQLException failed) {
            throw new StorageException("the tables could not be counted: " + failed.getMessage(), failed);
        }
        return counts;
    }

    /** Copies one table in batches, all of it or, on a failure, none of it. */
    private long copyTable(String table) throws SQLException {
        try (Connection from = source.connection();
                Connection to = target.connection();
                Statement reading = from.createStatement();
                ResultSet rows = reading.executeQuery("SELECT * FROM " + table)) {
            Map<String, Kind> targetKinds = kinds(to, table);
            List<Column> columns = columns(rows.getMetaData(), targetKinds, table.equals(website));
            boolean committing = to.getAutoCommit();
            to.setAutoCommit(false);
            try (PreparedStatement writing = to.prepareStatement(insertInto(table, columns))) {
                long written = send(rows, writing, columns);
                renumber(to, table);
                to.commit();
                return written;
            } catch (SQLException | RuntimeException failed) {
                to.rollback();
                throw failed;
            } finally {
                to.setAutoCommit(committing);
            }
        }
    }

    private static long send(ResultSet rows, PreparedStatement writing, List<Column> columns) throws SQLException {
        long written = 0;
        while (rows.next()) {
            for (int at = 0; at < columns.size(); at++) {
                columns.get(at).bind(rows, writing, at + 1);
            }
            writing.addBatch();
            if (++written % BATCH == 0) {
                writing.executeBatch();
            }
        }
        writing.executeBatch();
        return written;
    }

    /** A column both sides have, with the kind each side keeps it as. */
    private record Column(String name, int at, Kind from, Kind to, int type) {

        void bind(ResultSet rows, PreparedStatement writing, int into) throws SQLException {
            Object value =
                    switch (from) {
                        case FLAG -> rows.getBoolean(at);
                        case NUMBER -> rows.getLong(at);
                        case TEXT -> rows.getString(at);
                    };
            if (rows.wasNull() || value == null) {
                writing.setNull(into, type);
                return;
            }
            switch (to) {
                case FLAG -> writing.setBoolean(into, value instanceof Boolean flag ? flag : !"0".equals(text(value)));
                case NUMBER -> writing.setLong(into, value instanceof Boolean flag ? (flag ? 1 : 0) : number(value));
                case TEXT -> writing.setString(into, text(value));
            }
        }

        private static String text(Object value) {
            return String.valueOf(value);
        }

        private static long number(Object value) {
            return value instanceof Long number
                    ? number
                    : Long.parseLong(text(value).trim());
        }
    }

    /**
     * The columns to copy, in the source's order: every one the target has too. A website table moving into H2
     * leaves its numbers behind, because H2 would hand its next row a number the copy already used.
     */
    private List<Column> columns(ResultSetMetaData meta, Map<String, Kind> targetKinds, boolean websiteTable)
            throws SQLException {
        List<Column> columns = new ArrayList<>();
        for (int at = 1; at <= meta.getColumnCount(); at++) {
            String name = meta.getColumnName(at).toLowerCase(Locale.ROOT);
            Kind to = targetKinds.get(name);
            boolean renumbered = websiteTable && name.equals(WEBSITE_ID) && target.dialect() == Dialect.H2;
            if (to != null && !renumbered) {
                columns.add(new Column(name, at, kindOf(meta.getColumnType(at)), to, typeOf(to)));
            }
        }
        return columns;
    }

    private static Map<String, Kind> kinds(Connection connection, String table) throws SQLException {
        Map<String, Kind> kinds = new HashMap<>();
        try (Statement statement = connection.createStatement();
                ResultSet empty = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            ResultSetMetaData meta = empty.getMetaData();
            for (int at = 1; at <= meta.getColumnCount(); at++) {
                kinds.put(meta.getColumnName(at).toLowerCase(Locale.ROOT), kindOf(meta.getColumnType(at)));
            }
        }
        return kinds;
    }

    private static Kind kindOf(int type) {
        return switch (type) {
            case Types.BOOLEAN, Types.BIT -> Kind.FLAG;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> Kind.NUMBER;
            default -> Kind.TEXT;
        };
    }

    private static int typeOf(Kind kind) {
        return switch (kind) {
            case FLAG -> Types.BOOLEAN;
            case NUMBER -> Types.BIGINT;
            case TEXT -> Types.VARCHAR;
        };
    }

    /** The insert uxmLib's builder writes for these columns; the values are bound row by row. */
    private static String insertInto(String table, List<Column> columns) {
        InsertBuilder insert = InsertBuilder.into(table);
        for (Column column : columns) {
            insert.set(column.name(), column.name());
        }
        return insert.build().sql();
    }

    /** PostgreSQL's counter for a website's row numbers moves past the numbers the copy brought. */
    private void renumber(Connection connection, String table) throws SQLException {
        if (!table.equals(website) || target.dialect() != Dialect.POSTGRES) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT setval(pg_get_serial_sequence(?, ?), GREATEST(COALESCE(MAX(id), 0), 1)) FROM " + table)) {
            statement.setString(1, table);
            statement.setString(2, WEBSITE_ID);
            // The answer is the counter's new value, which nothing here needs.
            statement.executeQuery().close();
        }
    }
}
