package com.uxplima.auth.adapter.outbound;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.Disputes;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;

/** Disputes in the {@code uxmauth_dispute} table. The primary key on the name keeps the first one. */
public final class SqlDisputes implements Disputes {

    private static final String RAISE =
            "INSERT INTO uxmauth_dispute (name_key, premium_id, raised_at) VALUES (?, ?, ?)";
    private static final String READ = "SELECT premium_id, raised_at, kept FROM uxmauth_dispute WHERE name_key = ?";
    private static final String KEEP = "UPDATE uxmauth_dispute SET kept = ? WHERE name_key = ?";
    private static final String RESOLVE = "DELETE FROM uxmauth_dispute WHERE name_key = ?";

    private final Database database;

    public SqlDisputes(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    @Override
    public void raise(PlayerName name, UUID premiumId, Instant at) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(premiumId, "premiumId");
        Objects.requireNonNull(at, "at");
        try {
            database.inTransaction(sql -> sql.update(RAISE, statement -> {
                statement.setString(1, name.key());
                statement.setString(2, premiumId.toString());
                statement.setLong(3, at.toEpochMilli());
            }));
        } catch (StorageException held) {
            if (of(name).isEmpty()) {
                throw held;
            }
        }
    }

    @Override
    public Optional<Dispute> of(PlayerName name) {
        Objects.requireNonNull(name, "name");
        return database.transaction(sql -> sql.queryFirst(
                READ,
                statement -> statement.setString(1, name.key()),
                row -> new Dispute(
                        UUID.fromString(row.getString("premium_id")),
                        Instant.ofEpochMilli(row.getLong("raised_at")),
                        row.getBoolean("kept"))));
    }

    @Override
    public void resolve(PlayerName name) {
        Objects.requireNonNull(name, "name");
        database.inTransaction(sql -> sql.update(RESOLVE, statement -> statement.setString(1, name.key())));
    }

    @Override
    public void keep(PlayerName name) {
        Objects.requireNonNull(name, "name");
        database.inTransaction(sql -> sql.update(KEEP, statement -> {
            statement.setBoolean(1, true);
            statement.setString(2, name.key());
        }));
    }
}
