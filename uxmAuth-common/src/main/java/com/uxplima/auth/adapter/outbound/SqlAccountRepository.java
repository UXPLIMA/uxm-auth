package com.uxplima.auth.adapter.outbound;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;

/** Accounts in the {@code uxmauth_account} table, on whichever database the operator chose. */
public final class SqlAccountRepository implements AccountRepository {

    private static final String COLUMNS = "id, name, name_key, kind, premium_id, password, registered_at,"
            + " registration_address, last_login_at, last_address";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM uxmauth_account WHERE ";
    private static final String INSERT =
            "INSERT INTO uxmauth_account (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE uxmauth_account SET name = ?, name_key = ?, kind = ?,"
            + " premium_id = ?, password = ?, registered_at = ?, registration_address = ?, last_login_at = ?,"
            + " last_address = ? WHERE id = ?";
    private static final String COUNT_FROM =
            "SELECT COUNT(*) AS registered FROM uxmauth_account WHERE registration_address = ?";

    private final Database database;

    public SqlAccountRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public Optional<Account> findByName(PlayerName name) {
        Objects.requireNonNull(name, "name");
        return database.transaction(sql -> sql.queryFirst(
                SELECT + "name_key = ?", statement -> statement.setString(1, name.key()), SqlAccountRepository::read));
    }

    @Override
    public Optional<Account> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return database.transaction(sql -> sql.queryFirst(
                SELECT + "id = ?", statement -> statement.setString(1, id.toString()), SqlAccountRepository::read));
    }

    /**
     * The unique index on {@code name_key} decides. A read before the write would let two registrations both
     * see a free name; this reads only after the write failed, to tell the loser who holds the name.
     */
    @Override
    public Insert insert(Account account) {
        Objects.requireNonNull(account, "account");
        try {
            database.inTransaction(sql -> sql.update(INSERT, statement -> {
                statement.setString(1, account.id().toString());
                bindFields(statement, 2, account);
            }));
            return new Inserted();
        } catch (StorageException refused) {
            Optional<Account> holder = findByName(account.name());
            if (holder.isPresent()) {
                return new NameTaken(holder.get().name());
            }
            throw refused;
        }
    }

    @Override
    public void update(Account account) {
        Objects.requireNonNull(account, "account");
        database.inTransaction(sql -> sql.update(UPDATE, statement -> {
            bindFields(statement, 1, account);
            statement.setString(10, account.id().toString());
        }));
    }

    @Override
    public int countRegisteredFrom(String address) {
        Objects.requireNonNull(address, "address");
        return database.transaction(sql -> sql.queryFirst(
                        COUNT_FROM, statement -> statement.setString(1, address), row -> row.getInt("registered")))
                .orElse(0);
    }

    /** Binds every column but the id, in table order, starting at {@code first}. */
    private static void bindFields(PreparedStatement statement, int first, Account account) throws SQLException {
        int column = first;
        statement.setString(column++, account.name().value());
        statement.setString(column++, account.name().key());
        statement.setString(column++, account.kind().name());
        setText(
                statement,
                column++,
                account.premiumId() == null ? null : account.premiumId().toString());
        setText(
                statement,
                column++,
                account.password() == null ? null : account.password().encoded());
        statement.setLong(column++, account.registeredAt().toEpochMilli());
        statement.setString(column++, account.registrationAddress());
        setMillis(statement, column++, account.lastLoginAt());
        setText(statement, column, account.lastAddress());
    }

    private static Account read(ResultSet row) throws SQLException {
        String premiumId = row.getString("premium_id");
        String password = row.getString("password");
        long lastLogin = row.getLong("last_login_at");
        Instant lastLoginAt = row.wasNull() ? null : Instant.ofEpochMilli(lastLogin);
        return new Account(
                UUID.fromString(row.getString("id")),
                new PlayerName(row.getString("name")),
                AccountKind.valueOf(row.getString("kind")),
                premiumId == null ? null : UUID.fromString(premiumId),
                password == null ? null : new PasswordHash(password),
                Instant.ofEpochMilli(row.getLong("registered_at")),
                row.getString("registration_address"),
                lastLoginAt,
                row.getString("last_address"));
    }

    private static void setText(PreparedStatement statement, int column, @Nullable String value) throws SQLException {
        if (value == null) {
            statement.setNull(column, Types.VARCHAR);
        } else {
            statement.setString(column, value);
        }
    }

    private static void setMillis(PreparedStatement statement, int column, @Nullable Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(column, Types.BIGINT);
        } else {
            statement.setLong(column, value.toEpochMilli());
        }
    }
}
