package com.uxplima.auth.adapter.outbound;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.DeleteBuilder;
import com.uxplima.uxmlib.storage.sql.Dialect;
import com.uxplima.uxmlib.storage.sql.InsertBuilder;
import com.uxplima.uxmlib.storage.sql.SelectBuilder;
import com.uxplima.uxmlib.storage.sql.Sql;
import com.uxplima.uxmlib.storage.sql.UpdateBuilder;
import org.jspecify.annotations.Nullable;

/**
 * The rows of a website's login table, under the names the operator gave the table and its columns.
 *
 * <p>A name cannot be bound, so the statements are made by uxmLib's builders, which refuse anything but a plain
 * identifier; the settings refused one already. Every value is bound. The website keys a row by the name in lower
 * case and keeps the name as written beside it, and its dates are milliseconds since the epoch.
 *
 * <p>A link says which row is an account's, and is bound to that row by its mark: a row the website deleted and
 * somebody registered again under the name carries another mark, and is not taken for the account's.
 */
final class WebsiteRows {

    /** The column a website numbers its rows by, when its table has one. */
    static final String ID = "id";

    /**
     * A row as the website keeps it. A date it never wrote reads as zero.
     *
     * @param id the row's number, when the table numbers its rows
     */
    record Row(
            String name,
            String realName,
            String password,
            long registeredAt,
            @Nullable String registrationAddress,
            long lastLogin,
            @Nullable String lastAddress,
            @Nullable String id) {

        /**
         * What tells this row from a row made later under the same name: the moment it was registered, which a row
         * made again does not share; its number when it has no date; its password when it has neither, which then
         * binds only until the website changes it. The date comes before the number because a move into H2 numbers
         * the rows afresh. Hashed, so the table of links never holds a second copy of a password.
         */
        String mark() {
            String seen =
                    registeredAt > 0 ? "registered:" + registeredAt : id != null ? "id:" + id : "password:" + password;
            try {
                return HexFormat.of()
                        .formatHex(MessageDigest.getInstance("SHA-256").digest(seen.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException missing) {
                throw new IllegalStateException("the JDK has no SHA-256", missing);
            }
        }
    }

    /**
     * What is known of an account's link.
     *
     * @param mark the mark of the row it is bound to; empty for a link made before links were bound
     */
    record Link(@Nullable String mark) {

        /** Whether the link is bound to this row. A link with no mark yet binds the first row it meets. */
        boolean binds(Row row) {
            return mark == null || mark.equals(row.mark());
        }
    }

    private final Database database;
    private final Sql sql;
    private final String table;
    private final WebsiteTable.Columns columns;
    /** Whether the table numbers its rows, read once it is prepared. */
    private volatile boolean numbered;

    WebsiteRows(Database database, WebsiteTable website) {
        this.database = Objects.requireNonNull(database, "database");
        this.sql = new Sql(database);
        this.table = Objects.requireNonNull(website, "website").table();
        this.columns = website.columns();
    }

    /** Makes the table when it is missing. A table the website made is used as it stands. */
    void prepare() {
        WebsiteTable.Columns named = columns;
        sql.execute(String.format(
                Locale.ROOT,
                // The shape the website makes its table in: the names are the operator's, the types the website's.
                "CREATE TABLE IF NOT EXISTS %s (id %s, %s VARCHAR(255) NOT NULL UNIQUE, %s VARCHAR(255) NOT NULL,"
                        + " %s VARCHAR(255) NOT NULL, %s VARCHAR(40), %s BIGINT, %s BIGINT NOT NULL DEFAULT 0,"
                        + " %s VARCHAR(40))",
                table,
                idColumn(database.dialect()),
                named.name(),
                named.realName(),
                named.password(),
                named.ip(),
                named.lastLogin(),
                named.regDate(),
                named.regIp()));
        numbered = hasId();
    }

    private static String idColumn(Dialect dialect) {
        return switch (dialect) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL -> "INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY";
            case POSTGRES -> "SERIAL PRIMARY KEY";
            case H2, GENERIC -> "INTEGER AUTO_INCREMENT PRIMARY KEY";
        };
    }

    /** Whether the table, made here or by the website, has a column its rows are numbered by. */
    private boolean hasId() {
        try (Connection connection = database.connection();
                Statement statement = connection.createStatement();
                ResultSet empty = statement.executeQuery("SELECT * FROM " + table + " WHERE 1 = 0")) {
            ResultSetMetaData meta = empty.getMetaData();
            for (int at = 1; at <= meta.getColumnCount(); at++) {
                if (meta.getColumnName(at).equalsIgnoreCase(ID)) {
                    return true;
                }
            }
            return false;
        } catch (SQLException failed) {
            throw new StorageException(
                    "the website table " + table + " could not be read: " + failed.getMessage(), failed);
        }
    }

    Optional<Row> find(PlayerName name) {
        boolean withId = numbered;
        List<String> read = new ArrayList<>(List.of(
                columns.name(),
                columns.realName(),
                columns.password(),
                columns.regDate(),
                columns.regIp(),
                columns.lastLogin(),
                columns.ip()));
        if (withId) {
            read.add(ID);
        }
        return sql
                .query(
                        SelectBuilder.from(table)
                                .columns(read.toArray(String[]::new))
                                .where(columns.name(), key(name))
                                .limit(1)
                                .build(),
                        row -> new Row(
                                row.getString(1),
                                row.getString(2),
                                row.getString(3),
                                row.getLong(4),
                                row.getString(5),
                                row.getLong(6),
                                row.getString(7),
                                withId ? row.getString(8) : null))
                .stream()
                .findFirst();
    }

    /** Writes the account's password to its row, and makes the row when the website has none. */
    void write(Account account, PasswordHash password) {
        int changed = sql.update(UpdateBuilder.table(table)
                .set(columns.realName(), account.name().value())
                .set(columns.password(), password.encoded())
                .where(columns.name(), key(account.name()))
                .build());
        if (changed == 0) {
            add(account, password);
        }
    }

    /** Makes the account's row. False when the website holds the name already, and its row is left as it is. */
    boolean add(Account account, PasswordHash password) {
        try {
            sql.update(inserted(account, password).build());
            return true;
        } catch (StorageException clash) {
            if (find(account.name()).isPresent()) {
                return false;
            }
            throw clash;
        }
    }

    private InsertBuilder inserted(Account account, PasswordHash password) {
        InsertBuilder insert = InsertBuilder.into(table)
                .set(columns.name(), key(account.name()))
                .set(columns.realName(), account.name().value())
                .set(columns.password(), password.encoded())
                .set(columns.regDate(), account.registeredAt().toEpochMilli());
        if (!account.registrationAddress().isEmpty()) {
            insert.set(columns.regIp(), account.registrationAddress());
        }
        Instant lastLogin = account.lastLoginAt();
        String lastAddress = account.lastAddress();
        if (lastLogin != null && lastAddress != null) {
            insert.set(columns.lastLogin(), lastLogin.toEpochMilli()).set(columns.ip(), lastAddress);
        }
        return insert;
    }

    /** Replaces the password only while the row still holds the expected one. */
    boolean replace(PlayerName name, String expected, PasswordHash fresh) {
        return sql.update(UpdateBuilder.table(table)
                        .set(columns.password(), fresh.encoded())
                        .where(columns.name(), key(name))
                        .where(columns.password(), expected)
                        .build())
                == 1;
    }

    void recordLogin(PlayerName name, Instant at, String address) {
        sql.update(UpdateBuilder.table(table)
                .set(columns.lastLogin(), at.toEpochMilli())
                .set(columns.ip(), address)
                .where(columns.name(), key(name))
                .build());
    }

    void rename(PlayerName from, PlayerName to) {
        sql.update(UpdateBuilder.table(table)
                .set(columns.name(), key(to))
                .set(columns.realName(), to.value())
                .where(columns.name(), key(from))
                .build());
    }

    void delete(PlayerName name) {
        sql.update(DeleteBuilder.from(table).where(columns.name(), key(name)).build());
    }

    /** The account's link to a row, if it has one. */
    Optional<Link> link(UUID account) {
        return database.transaction(tx -> tx.queryFirst(
                "SELECT row_mark FROM uxmauth_website_link WHERE account_id = ?",
                statement -> statement.setString(1, account.toString()),
                row -> new Link(row.getString("row_mark"))));
    }

    /** Records that this row is the account's, bound to its mark in place of any row the link named before. */
    void link(UUID account, Row row) {
        String id = account.toString();
        String mark = row.mark();
        database.inTransaction(tx -> {
            int changed = tx.update("UPDATE uxmauth_website_link SET row_mark = ? WHERE account_id = ?", statement -> {
                statement.setString(1, mark);
                statement.setString(2, id);
            });
            if (changed == 0) {
                tx.update("INSERT INTO uxmauth_website_link (account_id, row_mark) VALUES (?, ?)", statement -> {
                    statement.setString(1, id);
                    statement.setString(2, mark);
                });
            }
        });
    }

    /** Binds the account's link to the row under its name now, as it stands after the plugin wrote it. */
    void relink(UUID account, PlayerName name) {
        find(name).ifPresent(row -> link(account, row));
    }

    /** Forgets the account's link, so no row under its name is the account's until one is linked again. */
    void unlink(UUID account) {
        database.inTransaction(tx -> tx.update(
                "DELETE FROM uxmauth_website_link WHERE account_id = ?",
                statement -> statement.setString(1, account.toString())));
    }

    private static String key(PlayerName name) {
        return name.value().toLowerCase(Locale.ROOT);
    }
}
