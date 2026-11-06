package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * The completed imports, one row each in the plugin's meta table, as {@code import.<source>} and a moment, and the
 * accounts each import made, in {@code uxmauth_import_account}.
 */
public final class SqlImportMarks implements ImportMarks {

    private static final String READ = "SELECT meta_value FROM uxmauth_meta WHERE meta_key = ?";
    private static final String UPDATE = "UPDATE uxmauth_meta SET meta_value = ? WHERE meta_key = ?";
    private static final String INSERT = "INSERT INTO uxmauth_meta (meta_key, meta_value) VALUES (?, ?)";
    private static final String WROTE =
            "SELECT account_id FROM uxmauth_import_account WHERE source = ? AND account_id = ?";
    private static final String WRITTEN = "INSERT INTO uxmauth_import_account (source, account_id) VALUES (?, ?)";

    private final Database database;

    public SqlImportMarks(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<Instant> completed(String key) {
        String row = rowOf(key);
        return database.transaction(sql -> sql.queryFirst(
                        READ, statement -> statement.setString(1, row), found -> found.getString("meta_value")))
                .map(Instant::parse);
    }

    @Override
    public void complete(String key, Instant at) {
        String row = rowOf(key);
        String value = Objects.requireNonNull(at, "at").toString();
        database.inTransaction(sql -> {
            int changed = sql.update(UPDATE, statement -> {
                statement.setString(1, value);
                statement.setString(2, row);
            });
            if (changed == 0) {
                sql.update(INSERT, statement -> {
                    statement.setString(1, row);
                    statement.setString(2, value);
                });
            }
        });
    }

    @Override
    public boolean wrote(String key, UUID account) {
        Objects.requireNonNull(key, "key");
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.queryFirst(
                        WROTE,
                        statement -> {
                            statement.setString(1, key);
                            statement.setString(2, id);
                        },
                        found -> found.getString("account_id")))
                .isPresent();
    }

    @Override
    public void written(String key, UUID account) {
        Objects.requireNonNull(key, "key");
        String id = Objects.requireNonNull(account, "account").toString();
        if (wrote(key, account)) {
            return;
        }
        database.inTransaction(sql -> sql.update(WRITTEN, statement -> {
            statement.setString(1, key);
            statement.setString(2, id);
        }));
    }

    private static String rowOf(String key) {
        return "import." + Objects.requireNonNull(key, "key");
    }
}
