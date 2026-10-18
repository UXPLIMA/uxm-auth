package com.uxplima.auth.adapter.outbound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.uxplima.auth.application.port.Emails;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.mail.EmailAddress;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.TxSql;

/**
 * Confirmed addresses in {@code uxmauth_email} and mailed codes in {@code uxmauth_mail_code}. A code changes under
 * a lock on its row, taken by writing the row's revision, so two servers that change one code at once do it one
 * after the other; the row that holds no code yet is made first, and two servers making it at once try again.
 */
public final class SqlEmails implements Emails {

    private static final int TRIES = 3;
    private static final String NONE = "";

    private static final String READ_ADDRESS = "SELECT address FROM uxmauth_email WHERE account_id = ?";
    private static final String DROP_ADDRESS = "DELETE FROM uxmauth_email WHERE account_id = ?";
    private static final String MOVE_ADDRESS =
            "UPDATE uxmauth_email SET address = ?, confirmed_at = ? WHERE account_id = ?";
    private static final String WRITE_ADDRESS =
            "INSERT INTO uxmauth_email (account_id, address, confirmed_at, login_code) VALUES (?, ?, ?, ?)";
    private static final String READ_LOGIN_CODE = "SELECT login_code FROM uxmauth_email WHERE account_id = ?";
    private static final String WRITE_LOGIN_CODE = "UPDATE uxmauth_email SET login_code = ? WHERE account_id = ?";
    private static final String LOCK =
            "UPDATE uxmauth_mail_code SET revision = revision + 1 WHERE account_id = ? AND purpose = ?";
    private static final String FIRST = "INSERT INTO uxmauth_mail_code (account_id, purpose, address, code_hash,"
            + " expires_at, attempts_left, sent_at, revision) VALUES (?, ?, '', '', 0, 0, 0, 0)";
    private static final String READ_CODE = "SELECT address, code_hash, expires_at, attempts_left, sent_at"
            + " FROM uxmauth_mail_code WHERE account_id = ? AND purpose = ?";
    private static final String WRITE_CODE = "UPDATE uxmauth_mail_code SET address = ?, code_hash = ?,"
            + " expires_at = ?, attempts_left = ?, sent_at = ? WHERE account_id = ? AND purpose = ?";

    private final Database database;
    private final Clock clock;

    public SqlEmails(Database database, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<EmailAddress> address(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.queryFirst(
                        READ_ADDRESS, statement -> statement.setString(1, id), row -> row.getString("address")))
                .flatMap(EmailAddress::parse);
    }

    @Override
    public void setAddress(UUID account, EmailAddress address) {
        String id = Objects.requireNonNull(account, "account").toString();
        String value = Objects.requireNonNull(address, "address").value();
        long now = clock.millis();
        database.inTransaction(sql -> {
            int moved = sql.update(MOVE_ADDRESS, statement -> {
                statement.setString(1, value);
                statement.setLong(2, now);
                statement.setString(3, id);
            });
            if (moved == 0) {
                sql.update(WRITE_ADDRESS, statement -> {
                    statement.setString(1, id);
                    statement.setString(2, value);
                    statement.setLong(3, now);
                    statement.setBoolean(4, false);
                });
            }
        });
    }

    @Override
    public void removeAddress(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> sql.update(DROP_ADDRESS, statement -> statement.setString(1, id)));
    }

    @Override
    public boolean mailsLoginCode(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.queryFirst(
                        READ_LOGIN_CODE, statement -> statement.setString(1, id), row -> row.getBoolean("login_code")))
                .orElse(false);
    }

    @Override
    public void setMailsLoginCode(UUID account, boolean on) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> sql.update(WRITE_LOGIN_CODE, statement -> {
            statement.setBoolean(1, on);
            statement.setString(2, id);
        }));
    }

    @Override
    public Optional<Code> code(UUID account, Purpose purpose) {
        String id = Objects.requireNonNull(account, "account").toString();
        Objects.requireNonNull(purpose, "purpose");
        return database.transaction(sql -> read(sql, id, purpose));
    }

    @Override
    public Optional<Code> changeCode(UUID account, Purpose purpose, UnaryOperator<Optional<Code>> change) {
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

    private static Optional<Code> changeLocked(
            TxSql sql, String id, Purpose purpose, UnaryOperator<Optional<Code>> change) {
        if (sql.update(LOCK, statement -> keyed(statement, 1, id, purpose)) == 0) {
            sql.update(FIRST, statement -> keyed(statement, 1, id, purpose));
        }
        Optional<Code> after = Objects.requireNonNull(change.apply(read(sql, id, purpose)), "change");
        sql.update(WRITE_CODE, statement -> {
            statement.setString(1, after.map(code -> code.to().value()).orElse(NONE));
            statement.setString(2, after.map(code -> code.hash().encoded()).orElse(NONE));
            statement.setLong(
                    3, after.map(code -> code.expires().toEpochMilli()).orElse(0L));
            statement.setInt(4, after.map(Code::attemptsLeft).orElse(0));
            statement.setLong(5, after.map(code -> code.sent().toEpochMilli()).orElse(0L));
            keyed(statement, 6, id, purpose);
        });
        return after;
    }

    private static void keyed(PreparedStatement statement, int first, String id, Purpose purpose) throws SQLException {
        statement.setString(first, id);
        statement.setString(first + 1, purpose.name());
    }

    /** A row as it is stored, before it is read as a code. */
    private record Row(String address, String hash, long expires, int attempts, long sent) {

        Optional<Code> code() {
            if (hash.isEmpty()) {
                return Optional.empty();
            }
            return EmailAddress.parse(address)
                    .map(to -> new Code(
                            to,
                            new PasswordHash(hash),
                            Instant.ofEpochMilli(expires),
                            attempts,
                            Instant.ofEpochMilli(sent)));
        }
    }

    private static Optional<Code> read(TxSql sql, String id, Purpose purpose) {
        return sql.queryFirst(
                        READ_CODE,
                        statement -> keyed(statement, 1, id, purpose),
                        row -> new Row(
                                row.getString("address"),
                                row.getString("code_hash"),
                                row.getLong("expires_at"),
                                row.getInt("attempts_left"),
                                row.getLong("sent_at")))
                .flatMap(Row::code);
    }
}
