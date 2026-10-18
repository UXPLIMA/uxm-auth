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
            + " registration_address, last_login_at, last_address, unverified";
    private static final String SELECT = "SELECT " + COLUMNS + " FROM uxmauth_account WHERE ";
    private static final String INSERT =
            "INSERT INTO uxmauth_account (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String UPDATE = "UPDATE uxmauth_account SET name = ?, name_key = ?, kind = ?,"
            + " premium_id = ?, password = ?, registered_at = ?, registration_address = ?, last_login_at = ?,"
            + " last_address = ?, unverified = ? WHERE id = ?";
    private static final String RECORD_LOGIN =
            "UPDATE uxmauth_account SET last_login_at = ?, last_address = ? WHERE id = ?";
    private static final String REPLACE_PASSWORD =
            "UPDATE uxmauth_account SET password = ? WHERE id = ? AND password = ?";
    private static final String SET_FIRST_PASSWORD =
            "UPDATE uxmauth_account SET password = ? WHERE id = ? AND password IS NULL";
    private static final String COUNT_FROM =
            "SELECT COUNT(*) AS registered FROM uxmauth_account WHERE registration_address = ?";

    private static final String RENAME = "UPDATE uxmauth_account SET name = ?, name_key = ? WHERE id = ?";
    private static final String REMEMBER_RENAME = "INSERT INTO uxmauth_rename (account_id, old_key, old_name,"
            + " new_name, renamed_at, told) VALUES (?, ?, ?, ?, ?, 0)";
    private static final String RENAMED_FROM = "SELECT a.name AS current_name FROM uxmauth_rename r"
            + " JOIN uxmauth_account a ON a.id = r.account_id WHERE r.old_key = ? ORDER BY r.renamed_at DESC";
    private static final String UNTOLD = "SELECT old_name, new_name FROM uxmauth_rename"
            + " WHERE account_id = ? AND told = 0 ORDER BY renamed_at DESC";
    private static final String TOLD = "UPDATE uxmauth_rename SET told = 1 WHERE account_id = ?";
    private static final String DELETE = "DELETE FROM uxmauth_account WHERE id = ?";
    private static final String DELETE_RENAMES = "DELETE FROM uxmauth_rename WHERE account_id = ?";
    private static final String MOVE_ID = "UPDATE uxmauth_account SET id = ? WHERE id = ?";
    private static final String DROP_SESSIONS = "DELETE FROM uxmauth_session WHERE account_id = ?";
    private static final String DROP_CHOICE = "DELETE FROM uxmauth_session_choice WHERE account_id = ?";
    private static final String MOVE_CHOICE = "UPDATE uxmauth_session_choice SET account_id = ? WHERE account_id = ?";
    private static final String MOVE_RENAMES = "UPDATE uxmauth_rename SET account_id = ? WHERE account_id = ?";

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
            statement.setString(11, account.id().toString());
        }));
    }

    @Override
    public void recordLogin(UUID id, Instant at, String address) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(address, "address");
        database.inTransaction(sql -> sql.update(RECORD_LOGIN, statement -> {
            statement.setLong(1, at.toEpochMilli());
            statement.setString(2, address);
            statement.setString(3, id.toString());
        }));
    }

    @Override
    public boolean replacePassword(UUID id, @Nullable PasswordHash expected, PasswordHash fresh) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(fresh, "fresh");
        String statementText = expected == null ? SET_FIRST_PASSWORD : REPLACE_PASSWORD;
        int changed = database.transaction(sql -> sql.update(statementText, statement -> {
            statement.setString(1, fresh.encoded());
            statement.setString(2, id.toString());
            if (expected != null) {
                statement.setString(3, expected.encoded());
            }
        }));
        return changed == 1;
    }

    @Override
    public int countRegisteredFrom(String address) {
        Objects.requireNonNull(address, "address");
        return database.transaction(sql -> sql.queryFirst(
                        COUNT_FROM, statement -> statement.setString(1, address), row -> row.getInt("registered")))
                .orElse(0);
    }

    @Override
    public Optional<Account> findByPremiumId(UUID premiumId) {
        Objects.requireNonNull(premiumId, "premiumId");
        return database.transaction(sql -> sql.queryFirst(
                SELECT + "premium_id = ?",
                statement -> statement.setString(1, premiumId.toString()),
                SqlAccountRepository::read));
    }

    /** The unique index on {@code name_key} decides here too; the move and its memory are one transaction. */
    @Override
    public Insert rename(UUID id, PlayerName to, Instant at) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(at, "at");
        Account before = findById(id).orElseThrow(() -> new IllegalArgumentException("no account " + id));
        try {
            database.inTransaction(sql -> {
                sql.update(RENAME, statement -> {
                    statement.setString(1, to.value());
                    statement.setString(2, to.key());
                    statement.setString(3, id.toString());
                });
                sql.update(REMEMBER_RENAME, statement -> {
                    statement.setString(1, id.toString());
                    statement.setString(2, before.name().key());
                    statement.setString(3, before.name().value());
                    statement.setString(4, to.value());
                    statement.setLong(5, at.toEpochMilli());
                });
            });
            return new Inserted();
        } catch (StorageException refused) {
            Optional<Account> holder = findByName(to);
            if (holder.isPresent()) {
                return new NameTaken(holder.get().name());
            }
            throw refused;
        }
    }

    @Override
    public Optional<PlayerName> renamedFrom(PlayerName old) {
        Objects.requireNonNull(old, "old");
        return database.transaction(sql -> sql.queryFirst(
                RENAMED_FROM,
                statement -> statement.setString(1, old.key()),
                row -> new PlayerName(row.getString("current_name"))));
    }

    @Override
    public Optional<Renamed> untoldRename(UUID id) {
        Objects.requireNonNull(id, "id");
        return database.transaction(sql -> sql.queryFirst(
                UNTOLD,
                statement -> statement.setString(1, id.toString()),
                row -> new Renamed(
                        new PlayerName(row.getString("old_name")), new PlayerName(row.getString("new_name")))));
    }

    @Override
    public void markRenameTold(UUID id) {
        Objects.requireNonNull(id, "id");
        database.inTransaction(sql -> sql.update(TOLD, statement -> statement.setString(1, id.toString())));
    }

    @Override
    public void delete(UUID id) {
        Objects.requireNonNull(id, "id");
        database.inTransaction(sql -> {
            sql.update(DELETE_RENAMES, statement -> statement.setString(1, id.toString()));
            // A remembered session must never let a later account under the same id in.
            sql.update(DROP_SESSIONS, statement -> statement.setString(1, id.toString()));
            sql.update(DROP_CHOICE, statement -> statement.setString(1, id.toString()));
            sql.update(DELETE, statement -> statement.setString(1, id.toString()));
        });
    }

    @Override
    public void moveId(UUID from, UUID to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        database.inTransaction(sql -> {
            sql.update(MOVE_ID, statement -> {
                statement.setString(1, to.toString());
                statement.setString(2, from.toString());
            });
            sql.update(MOVE_RENAMES, statement -> {
                statement.setString(1, to.toString());
                statement.setString(2, from.toString());
            });
            // The sessions of the old id stay behind; the choice is the player's and moves with them.
            sql.update(DROP_SESSIONS, statement -> statement.setString(1, from.toString()));
            sql.update(MOVE_CHOICE, statement -> {
                statement.setString(1, to.toString());
                statement.setString(2, from.toString());
            });
        });
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
        setText(statement, column++, account.lastAddress());
        statement.setInt(column, account.unverified() ? 1 : 0);
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
                row.getString("last_address"),
                row.getInt("unverified") != 0);
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
