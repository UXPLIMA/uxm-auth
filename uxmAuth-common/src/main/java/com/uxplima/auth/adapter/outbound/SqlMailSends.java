package com.uxplima.auth.adapter.outbound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.application.port.MailSends;
import com.uxplima.auth.domain.mail.SendTally;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.TxSql;
import org.jspecify.annotations.Nullable;

/**
 * The tallies in {@code uxmauth_mail_send}. A change bumps the row's revision before it reads the row, so the row is
 * locked for the rest of the transaction on every database this plugin speaks, and two servers sending at once
 * both count; when two first writes meet, the one that lost tries again and finds the row.
 */
public final class SqlMailSends implements MailSends {

    private static final int TRIES = 3;
    private static final long NEVER = 0L;

    private static final String LOCK =
            "UPDATE uxmauth_mail_send SET revision = revision + 1 WHERE account_id = ? AND purpose = ?";
    private static final String FIRST = "INSERT INTO uxmauth_mail_send"
            + " (account_id, purpose, last_sent, day_start, sent_today, revision) VALUES (?, ?, 0, 0, 0, 0)";
    private static final String READ =
            "SELECT last_sent, day_start, sent_today FROM uxmauth_mail_send" + " WHERE account_id = ? AND purpose = ?";
    private static final String WRITE = "UPDATE uxmauth_mail_send SET last_sent = ?, day_start = ?, sent_today = ?"
            + " WHERE account_id = ? AND purpose = ?";

    private final Database database;

    public SqlMailSends(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public SendTally change(UUID account, Emails.Purpose purpose, UnaryOperator<SendTally> change) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(change, "change");
        RuntimeException last = null;
        for (int tried = 0; tried < TRIES; tried++) {
            try {
                return database.transaction(sql -> changeLocked(sql, id, purpose, change));
            } catch (RuntimeException firstWritesMet) {
                last = firstWritesMet;
            }
        }
        throw Objects.requireNonNull(last, "last");
    }

    private static SendTally changeLocked(
            TxSql sql, String id, Emails.Purpose purpose, UnaryOperator<SendTally> change) {
        if (sql.update(LOCK, statement -> keyed(statement, 1, id, purpose)) == 0) {
            sql.update(FIRST, statement -> keyed(statement, 1, id, purpose));
        }
        SendTally before = read(sql, id, purpose).orElseGet(SendTally::none);
        SendTally after = Objects.requireNonNull(change.apply(before), "change");
        sql.update(WRITE, statement -> {
            statement.setLong(1, millis(after.last()));
            statement.setLong(2, millis(after.dayStart()));
            statement.setInt(3, after.sent());
            keyed(statement, 4, id, purpose);
        });
        return after;
    }

    private static Optional<SendTally> read(TxSql sql, String id, Emails.Purpose purpose) {
        return sql.queryFirst(
                READ,
                statement -> keyed(statement, 1, id, purpose),
                row -> new SendTally(
                        instant(row.getLong("last_sent")),
                        instant(row.getLong("day_start")),
                        row.getInt("sent_today")));
    }

    private static void keyed(PreparedStatement statement, int first, String id, Emails.Purpose purpose)
            throws SQLException {
        statement.setString(first, id);
        statement.setString(first + 1, purpose.name());
    }

    private static long millis(@Nullable Instant at) {
        return at == null ? NEVER : at.toEpochMilli();
    }

    private static @Nullable Instant instant(long millis) {
        return millis == NEVER ? null : Instant.ofEpochMilli(millis);
    }
}
