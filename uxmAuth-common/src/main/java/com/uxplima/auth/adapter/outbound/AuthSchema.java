package com.uxplima.auth.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmlib.storage.migration.MigrationResources;
import com.uxplima.uxmlib.storage.migration.MigrationRunner;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * Brings the tables up to date from the migrations in the jar, so an operator never runs SQL by hand.
 *
 * <p>The history is kept in a table of this plugin's own. Two plugins pointed at one MySQL with one shared
 * history would each read the other's version numbers as their own.
 */
public final class AuthSchema {

    /** Where the migrations are in the jar. */
    public static final String MIGRATIONS = "db/migration";

    /** The table that records which migrations ran. */
    public static final String HISTORY = "uxmauth_schema_history";

    private AuthSchema() {}

    /** Applies every migration this database has not seen, and hands the database back. */
    public static Database migrate(Database database) {
        Objects.requireNonNull(database, "database");
        new MigrationRunner(database, HISTORY)
                .apply(MigrationResources.loadRequired(AuthSchema.class.getClassLoader(), MIGRATIONS));
        return database;
    }
}
