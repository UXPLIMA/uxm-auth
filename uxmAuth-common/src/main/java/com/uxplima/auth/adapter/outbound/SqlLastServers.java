package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.LastServers;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * The last servers in the {@code uxmauth_last_server} table. A row is replaced in one transaction, deleted and
 * written again, which every database this plugin speaks runs alike.
 */
public final class SqlLastServers implements LastServers {

    private static final String READ = "SELECT server FROM uxmauth_last_server WHERE account_id = ?";
    private static final String FORGET = "DELETE FROM uxmauth_last_server WHERE account_id = ?";
    private static final String WRITE =
            "INSERT INTO uxmauth_last_server (account_id, server, seen_at) VALUES (?, ?, ?)";

    private final Database database;
    private final Clock clock;

    public SqlLastServers(Database database) {
        this(database, Clock.systemUTC());
    }

    public SqlLastServers(Database database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<String> of(UUID accountId) {
        Objects.requireNonNull(accountId, "accountId");
        return database.transaction(sql -> sql.queryFirst(
                READ, statement -> statement.setString(1, accountId.toString()), row -> row.getString("server")));
    }

    @Override
    public void remember(UUID accountId, String server) {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(server, "server");
        database.inTransaction(sql -> {
            sql.update(FORGET, statement -> statement.setString(1, accountId.toString()));
            sql.update(WRITE, statement -> {
                statement.setString(1, accountId.toString());
                statement.setString(2, server);
                statement.setLong(3, clock.millis());
            });
        });
    }
}
