package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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
 */
final class WebsiteRows {

    /** A row as the website keeps it. A date it never wrote reads as zero. */
    record Row(
            String name,
            String realName,
            String password,
            long registeredAt,
            @Nullable String registrationAddress,
            long lastLogin,
            @Nullable String lastAddress) {}

    private final Database database;
    private final Sql sql;
    private final String table;
    private final WebsiteTable.Columns columns;

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
    }

    private static String idColumn(Dialect dialect) {
        return switch (dialect) {
            case SQLITE -> "INTEGER PRIMARY KEY AUTOINCREMENT";
            case MYSQL -> "INTEGER NOT NULL AUTO_INCREMENT PRIMARY KEY";
            case POSTGRES -> "SERIAL PRIMARY KEY";
            case H2, GENERIC -> "INTEGER AUTO_INCREMENT PRIMARY KEY";
        };
    }

    Optional<Row> find(PlayerName name) {
        return sql
                .query(
                        SelectBuilder.from(table)
                                .columns(
                                        columns.name(),
                                        columns.realName(),
                                        columns.password(),
                                        columns.regDate(),
                                        columns.regIp(),
                                        columns.lastLogin(),
                                        columns.ip())
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
                                row.getString(7)))
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

    private static String key(PlayerName name) {
        return name.value().toLowerCase(Locale.ROOT);
    }
}
