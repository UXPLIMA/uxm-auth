package com.uxplima.auth.adapter.outbound;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * App secrets in {@code uxmauth_factor}, sealed before they are written, and backup codes in
 * {@code uxmauth_backup_code}, hashed before they reach this class. A step is taken by one conditional update, so
 * two servers that read the same code at once cannot both take it.
 */
public final class SqlFactors implements Factors {

    private static final String READ_APP = "SELECT secret, last_step FROM uxmauth_factor WHERE account_id = ?";
    private static final String DROP_APP = "DELETE FROM uxmauth_factor WHERE account_id = ?";
    private static final String WRITE_APP =
            "INSERT INTO uxmauth_factor (account_id, secret, last_step, created_at) VALUES (?, ?, -1, ?)";
    private static final String TAKE_STEP =
            "UPDATE uxmauth_factor SET last_step = ? WHERE account_id = ? AND last_step < ?";
    private static final String READ_CODES = "SELECT code_hash FROM uxmauth_backup_code WHERE account_id = ?";
    private static final String DROP_CODES = "DELETE FROM uxmauth_backup_code WHERE account_id = ?";
    private static final String WRITE_CODE = "INSERT INTO uxmauth_backup_code (account_id, code_hash) VALUES (?, ?)";
    private static final String SPEND_CODE = "DELETE FROM uxmauth_backup_code WHERE account_id = ? AND code_hash = ?";

    private final Database database;
    private final SecretSeal seal;
    private final Clock clock;

    public SqlFactors(Database database, SecretSeal seal, Clock clock) {
        this.database = Objects.requireNonNull(database, "database");
        this.seal = Objects.requireNonNull(seal, "seal");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<App> app(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.queryFirst(
                READ_APP,
                statement -> statement.setString(1, id),
                row -> new App(new TotpSecret(seal.open(row.getString("secret"))), row.getLong("last_step"))));
    }

    @Override
    public void setApp(UUID account, TotpSecret secret) {
        String id = Objects.requireNonNull(account, "account").toString();
        String sealed = seal.seal(Objects.requireNonNull(secret, "secret").bytes());
        long now = clock.millis();
        database.inTransaction(sql -> {
            sql.update(DROP_APP, statement -> statement.setString(1, id));
            sql.update(WRITE_APP, statement -> {
                statement.setString(1, id);
                statement.setString(2, sealed);
                statement.setLong(3, now);
            });
        });
    }

    @Override
    public void removeApp(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        database.inTransaction(sql -> sql.update(DROP_APP, statement -> statement.setString(1, id)));
    }

    @Override
    public boolean useStep(UUID account, long step) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.update(TAKE_STEP, statement -> {
                    statement.setLong(1, step);
                    statement.setString(2, id);
                    statement.setLong(3, step);
                }))
                == 1;
    }

    @Override
    public List<PasswordHash> backupCodes(UUID account) {
        String id = Objects.requireNonNull(account, "account").toString();
        return database.transaction(sql -> sql.query(
                READ_CODES,
                statement -> statement.setString(1, id),
                row -> new PasswordHash(row.getString("code_hash"))));
    }

    @Override
    public void setBackupCodes(UUID account, List<PasswordHash> codes) {
        String id = Objects.requireNonNull(account, "account").toString();
        List<PasswordHash> kept = List.copyOf(Objects.requireNonNull(codes, "codes"));
        database.inTransaction(sql -> {
            sql.update(DROP_CODES, statement -> statement.setString(1, id));
            for (PasswordHash code : kept) {
                sql.update(WRITE_CODE, statement -> {
                    statement.setString(1, id);
                    statement.setString(2, code.encoded());
                });
            }
        });
    }

    @Override
    public boolean useBackupCode(UUID account, PasswordHash code) {
        String id = Objects.requireNonNull(account, "account").toString();
        String hash = Objects.requireNonNull(code, "code").encoded();
        return database.transaction(sql -> sql.update(SPEND_CODE, statement -> {
                    statement.setString(1, id);
                    statement.setString(2, hash);
                }))
                == 1;
    }
}
