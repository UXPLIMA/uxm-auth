package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.uxmlib.storage.sql.Database;

/** The completed imports, one row each in the plugin's meta table, as {@code import.<source>} and a moment. */
public final class SqlImportMarks implements ImportMarks {

    private static final String READ = "SELECT meta_value FROM uxmauth_meta WHERE meta_key = ?";
    private static final String UPDATE = "UPDATE uxmauth_meta SET meta_value = ? WHERE meta_key = ?";
    private static final String INSERT = "INSERT INTO uxmauth_meta (meta_key, meta_value) VALUES (?, ?)";

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

    private static String rowOf(String key) {
        return "import." + Objects.requireNonNull(key, "key");
    }
}
