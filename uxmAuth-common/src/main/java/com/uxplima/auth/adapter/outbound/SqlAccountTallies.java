package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.AccountTallies;
import com.uxplima.auth.domain.protection.AddressTally;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.TxSql;
import org.jspecify.annotations.Nullable;

/**
 * The tallies in {@code uxmauth_account_lock}, kept as the address tallies are: a change bumps the row's revision
 * before it reads the row, so the row is locked for the rest of the transaction on every database this plugin
 * speaks, and two servers failing the same account at once both count. When two first writes meet, the one that
 * lost tries again and finds the row.
 */
public final class SqlAccountTallies implements AccountTallies {

    private static final int TRIES = 3;
    private static final long NEVER = 0L;

    private static final String LOCK = "UPDATE uxmauth_account_lock SET revision = revision + 1 WHERE account_id = ?";
    private static final String FIRST = "INSERT INTO uxmauth_account_lock"
            + " (account_id, failures, lock_step, locked_until, last_failure, revision) VALUES (?, 0, 0, 0, 0, 0)";
    private static final String READ = "SELECT failures, lock_step, locked_until, last_failure"
            + " FROM uxmauth_account_lock WHERE account_id = ?";
    private static final String WRITE = "UPDATE uxmauth_account_lock"
            + " SET failures = ?, lock_step = ?, locked_until = ?, last_failure = ? WHERE account_id = ?";
    private static final String CLEAR = "DELETE FROM uxmauth_account_lock WHERE account_id = ?";

    private final Database database;

    public SqlAccountTallies(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<AddressTally> find(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> read(sql, id));
    }

    @Override
    public AddressTally change(UUID account, UnaryOperator<AddressTally> change) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(change, "change");
        RuntimeException last = null;
        for (int tried = 0; tried < TRIES; tried++) {
            try {
                return database.transaction(sql -> changeLocked(sql, id, change));
            } catch (RuntimeException firstWritesMet) {
                last = firstWritesMet;
            }
        }
        throw Objects.requireNonNull(last, "last");
    }

    private static AddressTally changeLocked(TxSql sql, String id, UnaryOperator<AddressTally> change) {
        if (sql.update(LOCK, statement -> statement.setString(1, id)) == 0) {
            sql.update(FIRST, statement -> statement.setString(1, id));
        }
        AddressTally before = read(sql, id).orElseGet(AddressTally::clean);
        AddressTally after = Objects.requireNonNull(change.apply(before), "change");
        sql.update(WRITE, statement -> {
            statement.setInt(1, after.failures());
            statement.setInt(2, after.step());
            statement.setLong(3, millis(after.bannedUntil()));
            statement.setLong(4, millis(after.lastFailure()));
            statement.setString(5, id);
        });
        return after;
    }

    @Override
    public void clear(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> sql.update(CLEAR, statement -> statement.setString(1, id)));
    }

    private static Optional<AddressTally> read(TxSql sql, String id) {
        return sql.queryFirst(
                READ,
                statement -> statement.setString(1, id),
                row -> new AddressTally(
                        row.getInt("failures"),
                        row.getInt("lock_step"),
                        instant(row.getLong("locked_until")),
                        instant(row.getLong("last_failure"))));
    }

    private static long millis(@Nullable Instant at) {
        return at == null ? NEVER : at.toEpochMilli();
    }

    private static @Nullable Instant instant(long millis) {
        return millis == NEVER ? null : Instant.ofEpochMilli(millis);
    }
}
