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

    /**
     * One lock the servers of a database take turns at.
     *
     * @param work what the lock guards, as a console line names it
     * @param name the name MySQL and MariaDB key the lock by
     * @param key the advisory key PostgreSQL keys it by, fixed here so every server agrees
     */
    record Lock(String work, String name, long key) {}

    /** Held while the tables are brought up to date. */
    static final Lock SCHEMA = new Lock("schema", "uxmauth_schema", 0x7578_6d61_7574_6801L);

    /** Held while another plugin's accounts are imported, so two servers starting together import once. */
    static final Lock IMPORT = new Lock("import", "uxmauth_import", 0x7578_6d61_7574_6802L);

    /** How long a server waits for another server's migrations, in seconds. */
    private static final int WAIT_SECONDS = 300;

    private SchemaLock() {}

    /** Whether servers share this kind of database, and so take turns at its migrations. */
    static boolean shared(Dialect dialect) {
        return dialect == Dialect.MYSQL || dialect == Dialect.POSTGRES;
    }

    /** Runs the migrations inside the schema lock, and gives the lock back however they end. */
    static void holding(Database database, Runnable migrations) {
        holding(database, SCHEMA, migrations);
    }

    /** Runs the work inside the lock, and gives the lock back however it ends. */
    static void holding(Database database, Lock lock, Runnable work) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(work, "work");
        switch (database.dialect()) {
            case MYSQL -> held(database, lock, work, "SELECT GET_LOCK(?, ?)", "SELECT RELEASE_LOCK(?)", true);
            case POSTGRES ->
                held(database, lock, work, "SELECT pg_advisory_lock(?)", "SELECT pg_advisory_unlock(?)", false);
            case SQLITE, H2, GENERIC -> work.run();
        }
    }

    private static void held(Database database, Lock lock, Runnable work, String take, String give, boolean named) {
        try (Connection connection = database.connection()) {
            if (!ask(connection, lock, take, named, true)) {
                throw new StorageException(
                        "another server kept the " + lock.work() + " lock for " + WAIT_SECONDS
                                + " seconds; start this one again once that one is up",
                        new SQLException(lock.name()));
            }
            try {
                work.run();
            } finally {
                ask(connection, lock, give, named, false);
            }
        } catch (SQLException failure) {
            throw new StorageException("the " + lock.work() + " lock could not be taken", failure);
        }
    }

    /** Sends one lock statement and answers whether the database granted it. */
    private static boolean ask(Connection connection, Lock lock, String sql, boolean named, boolean waiting)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (named) {
                statement.setString(1, lock.name());
                if (waiting) {
                    statement.setInt(2, WAIT_SECONDS);
                }
            } else {
                statement.setLong(1, lock.key());
            }
            try (ResultSet answer = statement.executeQuery()) {
                // PostgreSQL's pg_advisory_lock answers void once it holds the lock; only GET_LOCK can say no.
                return !named || (answer.next() && answer.getInt(1) == 1);
            }
        }
    }
}
