package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.RememberedSessions;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * Remembered sessions in {@code uxmauth_session} and choices in {@code uxmauth_session_choice}. A row is replaced
 * by deleting and writing it again in one transaction, which every database this plugin speaks runs alike.
 */
public final class SqlRememberedSessions implements RememberedSessions {

    private static final String DROP_ONE = "DELETE FROM uxmauth_session WHERE account_id = ? AND address = ?";
    private static final String DROP_EXPIRED = "DELETE FROM uxmauth_session WHERE account_id = ? AND expires_at <= ?";
    private static final String WRITE =
            "INSERT INTO uxmauth_session (account_id, address, expires_at) VALUES (?, ?, ?)";
    private static final String HOLDS =
            "SELECT expires_at FROM uxmauth_session WHERE account_id = ? AND address = ? AND expires_at > ?";
    private static final String FORGET = "DELETE FROM uxmauth_session WHERE account_id = ?";
    private static final String READ_CHOICE = "SELECT remember FROM uxmauth_session_choice WHERE account_id = ?";
    private static final String DROP_CHOICE = "DELETE FROM uxmauth_session_choice WHERE account_id = ?";
    private static final String WRITE_CHOICE =
            "INSERT INTO uxmauth_session_choice (account_id, remember) VALUES (?, ?)";

    private final Database database;
    private final Clock clock;

    public SqlRememberedSessions(Database database) {
        this(database, Clock.systemUTC());
    }

    public SqlRememberedSessions(Database database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void remember(UUID account, String address, Instant until) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(address, "address");
        long expires = Objects.requireNonNull(until, "until").toEpochMilli();
        long now = clock.millis();
        database.inTransaction(sql -> {
            // What ran out goes as a new session is written, so the table holds no more than live ones.
            sql.update(DROP_EXPIRED, statement -> {
                statement.setString(1, id);
                statement.setLong(2, now);
            });
            sql.update(DROP_ONE, statement -> {
                statement.setString(1, id);
                statement.setString(2, address);
            });
            sql.update(WRITE, statement -> {
                statement.setString(1, id);
                statement.setString(2, address);
                statement.setLong(3, expires);
            });
        });
    }

    @Override
    public boolean holds(UUID account, String address, Instant now) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(address, "address");
        long at = Objects.requireNonNull(now, "now").toEpochMilli();
        return database.transaction(sql -> sql.queryFirst(
                        HOLDS,
                        statement -> {
                            statement.setString(1, id);
                            statement.setString(2, address);
                            statement.setLong(3, at);
                        },
                        row -> row.getLong("expires_at")))
                .isPresent();
    }

    @Override
    public void forget(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> sql.update(FORGET, statement -> statement.setString(1, id)));
    }

    @Override
    public Optional<Boolean> choice(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.queryFirst(
                READ_CHOICE, statement -> statement.setString(1, id), row -> row.getBoolean("remember")));
    }

    @Override
    public void choose(UUID account, boolean remember) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> {
            sql.update(DROP_CHOICE, statement -> statement.setString(1, id));
            sql.update(WRITE_CHOICE, statement -> {
                statement.setString(1, id);
                statement.setBoolean(2, remember);
            });
        });
    }
}
