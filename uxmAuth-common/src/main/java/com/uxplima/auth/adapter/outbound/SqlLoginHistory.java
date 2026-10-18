package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.state.AuthMethod;
import com.uxplima.uxmlib.storage.sql.Database;

/** The logins in {@code uxmauth_login}, one row each, written once and never changed. */
public final class SqlLoginHistory implements LoginHistory {

    private static final String WRITE =
            "INSERT INTO uxmauth_login (account_id, at, address, address_key, method) VALUES (?, ?, ?, ?, ?)";
    private static final String RECENT =
            "SELECT at, address, method FROM uxmauth_login WHERE account_id = ? ORDER BY at DESC LIMIT ?";
    private static final String COUNT = "SELECT COUNT(*) AS logins FROM uxmauth_login WHERE account_id = ?";
    private static final String ON_ADDRESS =
            "SELECT DISTINCT account_id FROM uxmauth_login WHERE address_key = ? ORDER BY account_id LIMIT ?";
    private static final String PRUNE = "DELETE FROM uxmauth_login WHERE at < ?";

    private final Database database;

    public SqlLoginHistory(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void record(UUID account, Entry entry) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(entry, "entry");
        database.inTransaction(sql -> sql.update(WRITE, statement -> {
            statement.setString(1, id);
            statement.setLong(2, entry.at().toEpochMilli());
            statement.setString(3, entry.address());
            statement.setString(4, AddressKey.of(entry.address()));
            statement.setString(5, entry.method().name());
        }));
    }

    @Override
    public List<Entry> recent(UUID account, int limit) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.query(
                RECENT,
                statement -> {
                    statement.setString(1, id);
                    statement.setInt(2, Math.max(0, limit));
                },
                row -> new Entry(
                        Instant.ofEpochMilli(row.getLong("at")),
                        row.getString("address"),
                        AuthMethod.valueOf(row.getString("method")))));
    }

    @Override
    public int count(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql ->
                        sql.queryFirst(COUNT, statement -> statement.setString(1, id), row -> row.getInt("logins")))
                .orElse(0);
    }

    @Override
    public List<UUID> accountsOn(String address, int limit) {
        String key = AddressKey.of(Objects.requireNonNull(address, "address"));
        return database.transaction(sql -> sql.query(
                ON_ADDRESS,
                statement -> {
                    statement.setString(1, key);
                    statement.setInt(2, Math.max(0, limit));
                },
                row -> UUID.fromString(row.getString("account_id"))));
    }

    @Override
    public int prune(Instant before) {
        long until = Objects.requireNonNull(before, "before").toEpochMilli();
        return database.transaction(sql -> sql.update(PRUNE, statement -> statement.setLong(1, until)));
    }
}
