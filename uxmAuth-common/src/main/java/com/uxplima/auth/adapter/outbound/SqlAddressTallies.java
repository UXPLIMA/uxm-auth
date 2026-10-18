package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.AddressTallies;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.TxSql;
import org.jspecify.annotations.Nullable;

/**
 * The tallies in {@code uxmauth_address}. A change bumps the row's revision before it reads the row, so the row
 * is locked for the rest of the transaction on every database this plugin speaks, and two servers failing the
 * same address at once both count. An address seen for the first time is written clean; when two first writes
 * meet, the one that lost tries again and finds the row.
 */
public final class SqlAddressTallies implements AddressTallies {

    private static final int TRIES = 3;
    private static final long NEVER = 0L;

    private static final String LOCK = "UPDATE uxmauth_address SET revision = revision + 1 WHERE address = ?";
    private static final String FIRST = "INSERT INTO uxmauth_address"
            + " (address, failures, ban_step, banned_until, last_failure, revision) VALUES (?, 0, 0, 0, 0, 0)";
    private static final String READ =
            "SELECT failures, ban_step, banned_until, last_failure FROM uxmauth_address WHERE address = ?";
    private static final String WRITE = "UPDATE uxmauth_address"
            + " SET failures = ?, ban_step = ?, banned_until = ?, last_failure = ? WHERE address = ?";
    private static final String CLEAR = "DELETE FROM uxmauth_address WHERE address = ?";

    private final Database database;

    public SqlAddressTallies(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<AddressTally> find(String key) {
        Objects.requireNonNull(key, "key");
        return database.transaction(sql -> read(sql, key));
    }

    @Override
    public AddressTally change(String key, UnaryOperator<AddressTally> change) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(change, "change");
        RuntimeException last = null;
        for (int tried = 0; tried < TRIES; tried++) {
            try {
                return database.transaction(sql -> changeLocked(sql, key, change));
            } catch (RuntimeException firstWritesMet) {
                last = firstWritesMet;
            }
        }
        throw Objects.requireNonNull(last, "last");
    }

    private static AddressTally changeLocked(TxSql sql, String key, UnaryOperator<AddressTally> change) {
        if (sql.update(LOCK, statement -> statement.setString(1, key)) == 0) {
            sql.update(FIRST, statement -> statement.setString(1, key));
        }
        AddressTally before = read(sql, key).orElseGet(AddressTally::clean);
        AddressTally after = Objects.requireNonNull(change.apply(before), "change");
        sql.update(WRITE, statement -> {
            statement.setInt(1, after.failures());
            statement.setInt(2, after.step());
            statement.setLong(3, millis(after.bannedUntil()));
            statement.setLong(4, millis(after.lastFailure()));
            statement.setString(5, key);
        });
        return after;
    }

    @Override
    public void clear(String key) {
        Objects.requireNonNull(key, "key");
        database.inTransaction(sql -> sql.update(CLEAR, statement -> statement.setString(1, key)));
    }

    private static Optional<AddressTally> read(TxSql sql, String key) {
        return sql.queryFirst(
                READ,
                statement -> statement.setString(1, key),
                row -> new AddressTally(
                        row.getInt("failures"),
                        row.getInt("ban_step"),
                        instant(row.getLong("banned_until")),
                        instant(row.getLong("last_failure"))));
    }

    private static long millis(@Nullable Instant at) {
        return at == null ? NEVER : at.toEpochMilli();
    }

    private static @Nullable Instant instant(long millis) {
        return millis == NEVER ? null : Instant.ofEpochMilli(millis);
    }
}
