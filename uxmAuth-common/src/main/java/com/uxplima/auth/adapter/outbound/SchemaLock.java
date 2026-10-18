package com.uxplima.auth.adapter.outbound;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Dialect;

/**
 * Keeps two servers that start together on one database from running one migration twice.
 *
 * <p>The network lab started two servers on one MariaDB at the same moment, and the second failed on an index the
 * first had just made. The lock is the database's own, held on one connection while the migrations run on others,
 * so a server waits for another server and never for itself. A file database belongs to one server and takes none.
 */
final class SchemaLock {

    private static final String NAME = "uxmauth_schema";

    /** The advisory key PostgreSQL keys its lock by: the name's hash, fixed here so every server agrees. */
    private static final long KEY = 0x7578_6d61_7574_6801L;

    /** How long a server waits for another server's migrations, in seconds. */
    private static final int WAIT_SECONDS = 300;

    private SchemaLock() {}

    /** Whether servers share this kind of database, and so take turns at its migrations. */
    static boolean shared(Dialect dialect) {
        return dialect == Dialect.MYSQL || dialect == Dialect.POSTGRES;
    }

    /** Runs the migrations inside the database's lock, and gives the lock back however they end. */
    static void holding(Database database, Runnable migrations) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(migrations, "migrations");
        switch (database.dialect()) {
            case MYSQL -> held(database, migrations, "SELECT GET_LOCK(?, ?)", "SELECT RELEASE_LOCK(?)", true);
            case POSTGRES ->
                held(database, migrations, "SELECT pg_advisory_lock(?)", "SELECT pg_advisory_unlock(?)", false);
            case SQLITE, H2, GENERIC -> migrations.run();
        }
    }

    private static void held(Database database, Runnable migrations, String take, String give, boolean named) {
        try (Connection connection = database.connection()) {
            if (!ask(connection, take, named, true)) {
                throw new StorageException(
                        "another server kept the schema lock for " + WAIT_SECONDS
                                + " seconds; start this one again once that one is up",
                        new SQLException(NAME));
            }
            try {
                migrations.run();
            } finally {
                ask(connection, give, named, false);
            }
        } catch (SQLException failure) {
            throw new StorageException("the schema lock could not be taken", failure);
        }
    }

    /** Sends one lock statement and answers whether the database granted it. */
    private static boolean ask(Connection connection, String sql, boolean named, boolean waiting) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (named) {
                statement.setString(1, NAME);
                if (waiting) {
                    statement.setInt(2, WAIT_SECONDS);
                }
            } else {
                statement.setLong(1, KEY);
            }
            try (ResultSet answer = statement.executeQuery()) {
                // PostgreSQL's pg_advisory_lock answers void once it holds the lock; only GET_LOCK can say no.
                return !named || (answer.next() && answer.getInt(1) == 1);
            }
        }
    }
}
