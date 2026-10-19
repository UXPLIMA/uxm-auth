package com.uxplima.auth.adapter.outbound;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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

    private AuthDatabase() {}

    /** Opens the database, with the driver loaded and every migration applied. */
    public static Database open(PluginSettings.Storage storage, String poolName) {
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(poolName, "poolName");
        load(driverOf(storage.jdbcUrl()));
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
        String scheme = schemeOf(jdbcUrl);
        String driver = DRIVERS.get(scheme);
        if (driver == null) {
            throw new IllegalArgumentException("storage.jdbc-url starts with jdbc:" + scheme
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
        return schemeOf(jdbcUrl).equals("mysql")
                ? "jdbc:mariadb:" + jdbcUrl.substring("jdbc:mysql:".length())
                : jdbcUrl;
    }

    private static String schemeOf(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        String lower = jdbcUrl.toLowerCase(Locale.ROOT);
        int colon = lower.indexOf(':', "jdbc:".length());
        if (!lower.startsWith("jdbc:") || colon < 0) {
            throw new IllegalArgumentException("storage.jdbc-url is not a JDBC address: " + jdbcUrl);
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
