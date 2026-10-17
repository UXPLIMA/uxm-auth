package com.uxplima.auth.adapter.outbound;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * The UUID scheme of the first start, written in the database beside the accounts it made.
 *
 * <p>Every other plugin keys its data by the UUID, so a scheme changed in config.conf after the first start
 * would quietly put every player into an empty inventory. A start under another scheme than the recorded one
 * is refused instead, and the operator is pointed at the command that moves the accounts.
 */
public final class RecordedScheme {

    private static final String KEY = "uuid-scheme";
    private static final String READ = "SELECT meta_value FROM uxmauth_meta WHERE meta_key = ?";
    private static final String WRITE = "INSERT INTO uxmauth_meta (meta_key, meta_value) VALUES (?, ?)";

    private final Database database;

    public RecordedScheme(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /** The scheme the first start wrote down, if a start has happened. */
    public Optional<UuidScheme> recorded() {
        return database.transaction(sql -> sql.queryFirst(
                        READ, statement -> statement.setString(1, KEY), row -> row.getString("meta_value")))
                .map(RecordedScheme::scheme);
    }

    /**
     * Writes the configured scheme down on the first start, and refuses a later start under another one. Two
     * servers on one database starting at once both try the write; the loser reads what the winner wrote.
     */
    public void check(UuidScheme configured) {
        Objects.requireNonNull(configured, "configured");
        Optional<UuidScheme> recorded = recorded();
        if (recorded.isEmpty()) {
            try {
                database.inTransaction(sql -> sql.update(WRITE, statement -> {
                    statement.setString(1, KEY);
                    statement.setString(2, configured.key());
                }));
                return;
            } catch (StorageException lost) {
                recorded = recorded();
                if (recorded.isEmpty()) {
                    throw lost;
                }
            }
        }
        if (recorded.get() != configured) {
            throw new SchemeChanged("ownership.uuid-scheme is " + configured.key() + ", and the accounts were "
                    + "made under " + recorded.get().key() + ". Changing it would give every player a new UUID and "
                    + "an empty inventory in every other plugin. Put "
                    + recorded.get().key()
                    + " back, or move the accounts with /uxmauth migrate-uuids.");
        }
    }

    private static UuidScheme scheme(String written) {
        return Arrays.stream(UuidScheme.values())
                .filter(scheme -> scheme.key().equals(written))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "The database records the UUID scheme " + written + ", which this plugin does not know"));
    }
}
