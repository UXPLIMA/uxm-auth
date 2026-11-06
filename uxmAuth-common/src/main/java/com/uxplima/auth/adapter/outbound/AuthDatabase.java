package com.uxplima.auth.adapter.outbound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.uxmlib.storage.sql.Database;

/**
 * Opens the database the settings name and brings its tables up to date, on Paper, Folia and Velocity alike.
 *
 * <p>A driver in a plugin jar is invisible to the pool until its class is loaded: {@code DriverManager} looked
 * for drivers once, through the server's own class loader, long before this jar was opened. Paper happens to
 * ship MySQL's and SQLite's and Velocity ships none, so the driver the address needs is loaded here by name,
 * from this jar, and every platform opens every database the same way.
 */
public final class AuthDatabase {

    /** The JDBC scheme of each database this jar carries a driver for, and that driver. */
    private static final Map<String, String> DRIVERS = Map.of(
            "sqlite", "org.sqlite.JDBC",
            "mariadb", "org.mariadb.jdbc.Driver",
            "mysql", "org.mariadb.jdbc.Driver",
            "postgresql", "org.postgresql.Driver",
            "h2", "org.h2.Driver");

    /** What a database's name in an address may be. */
    private static final Pattern SCHEME = Pattern.compile("[a-z0-9]+");

    private AuthDatabase() {}

    /** Opens the database, with the driver loaded and every migration applied. */
    public static Database open(PluginSettings.Storage storage, String poolName) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(poolName, "poolName");
        load(driverOf(storage.jdbcUrl(), "storage.jdbc-url"));
        Database database = pool(storage, poolName, storage.poolSize());
        if (!SchemaLock.shared(database.dialect())) {
            return AuthSchema.migrate(database);
        }
        // The lock has a pool of its own, so a pool of one connection still has one left for the migrations.
        try (Database lock = pool(storage, poolName + "-schema", 1)) {
            SchemaLock.holding(lock, () -> AuthSchema.migrate(database));
        }
        return database;
    }

    /**
     * Runs the work while this server holds the import lock, on a pool of its own. A database only one server
     * opens needs no lock.
     */
    public static void importing(Database database, PluginSettings.Storage storage, String poolName, Runnable work) {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(work, "work");
        if (!SchemaLock.shared(database.dialect())) {
            work.run();
            return;
        }
        try (Database lock = pool(storage, Objects.requireNonNull(poolName, "poolName") + "-import", 1)) {
            SchemaLock.holding(lock, SchemaLock.IMPORT, work);
        }
    }

    private static Database pool(PluginSettings.Storage storage, String poolName, int size) {
        return Database.builder()
                .poolName(poolName)
                .jdbcUrl(addressOf(storage.jdbcUrl()))
                .username(storage.username())
                .password(storage.password())
                .maxPoolSize(size)
                .build();
    }

    /** The driver class this jar carries for the address. */
    static String driverOf(String jdbcUrl) {
        return driverOf(jdbcUrl, "storage.jdbc-url");
    }

    /** The driver class this jar carries for the address, refused under the configuration key it was read from. */
    static String driverOf(String jdbcUrl, String key) {
        String scheme = schemeOf(jdbcUrl, key);
        String driver = DRIVERS.get(scheme);
        if (driver == null) {
            throw new IllegalArgumentException(key + " starts with jdbc:" + scheme
                    + ", and this plugin speaks sqlite, mysql, mariadb, postgresql and h2");
        }
        return driver;
    }

    /**
     * The address the driver is handed. MariaDB's driver speaks MySQL and takes a {@code jdbc:mysql:} address
     * only when told to, so a MySQL address is handed to it under its own scheme.
     */
    static String addressOf(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        return schemeOf(jdbcUrl, "storage.jdbc-url").equals("mysql")
                ? "jdbc:mariadb:" + jdbcUrl.substring("jdbc:mysql:".length())
                : jdbcUrl;
    }

    /**
     * The address as a message may show it: the database and the host, never a user, a password or a parameter,
     * which an address may carry and a console line or a stack trace in a bug report would then hand on.
     */
    public static String shown(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':', "jdbc:".length());
        if (!lower.startsWith("jdbc:")
                || colon < 0
                || !SCHEME.matcher(lower.substring("jdbc:".length(), colon)).matches()) {
            return "an address that does not start with jdbc:<database>:";
        }
        String scheme = lower.substring(0, colon);
        String rest = jdbcUrl.substring(colon + 1);
        if (!rest.startsWith("//")) {
            return scheme;
        }
        String authority = rest.substring(2).split("[/?;]", 2)[0];
        return scheme + "://" + authority.substring(authority.lastIndexOf('@') + 1);
    }

    private static String schemeOf(String jdbcUrl, String key) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':', "jdbc:".length());
        // The address is never repeated: it may hold the database's password.
        if (!lower.startsWith("jdbc:")
                || colon < 0
                || !SCHEME.matcher(lower.substring("jdbc:".length(), colon)).matches()) {
            throw new IllegalArgumentException(
                    key + " is not a JDBC address: it starts with jdbc:<database>:, such as jdbc:mysql://host/db");
        }
        return lower.substring("jdbc:".length(), colon);
    }

    static void load(String driver) {
        try {
            Class.forName(driver, true, AuthDatabase.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(
                    "The " + driver + " driver is not in this jar. The jar is not the shaded one.", missing);
        }
    }
}
