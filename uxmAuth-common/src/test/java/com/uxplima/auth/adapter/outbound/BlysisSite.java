package com.uxplima.auth.adapter.outbound;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.Sql;

/**
 * A Blysis database for a test: its {@code "User"}, {@code "Role"} and {@code "UserRole"} tables as Prisma makes them,
 * with the member role in it.
 *
 * <p>H2 in its PostgreSQL mode always. A real PostgreSQL too when {@value #ENVIRONMENT} names one, such as
 * {@code jdbc:postgresql://127.0.0.1:5432/test?user=test&password=test}: each test gets a schema of its own there.
 */
enum BlysisSite {
    H2_POSTGRES_MODE,
    POSTGRES;

    /** The environment variable that names a PostgreSQL server for the tests, or nothing to skip it. */
    static final String ENVIRONMENT = "UXMAUTH_TEST_POSTGRES";

    static final String MEMBER_ROLE = "role-member";

    /** The sites this run can reach. */
    static List<BlysisSite> reachable() {
        String url = System.getenv(ENVIRONMENT);
        return url == null || url.isBlank() ? List.of(H2_POSTGRES_MODE) : List.of(H2_POSTGRES_MODE, POSTGRES);
    }

    /** An empty Blysis database with the member role. */
    Database open() {
        Database database =
                switch (this) {
                    case H2_POSTGRES_MODE ->
                        Database.builder()
                                .jdbcUrl("jdbc:h2:mem:" + UUID.randomUUID()
                                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1")
                                .build();
                    case POSTGRES -> {
                        String schema = "blysis_" + UUID.randomUUID().toString().replace("-", "");
                        String url = System.getenv(ENVIRONMENT);
                        try (Database admin =
                                Database.builder().jdbcUrl(url).maxPoolSize(1).build()) {
                            new Sql(admin).execute("CREATE SCHEMA " + schema);
                        }
                        yield Database.builder()
                                .jdbcUrl(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema)
                                .maxPoolSize(2)
                                .build();
                    }
                };
        Sql sql = new Sql(database);
        // The types and keys of Blysis' schema.core.prisma, as prisma migrate writes them for PostgreSQL. H2 reads
        // TEXT as a large object, which may not be a key, so it takes VARCHAR there; the statements are the same.
        String text = this == H2_POSTGRES_MODE ? "VARCHAR(1000)" : "TEXT";
        sql.execute(String.format(
                Locale.ROOT,
                "CREATE TABLE \"Role\" (\"id\" %1$s NOT NULL, \"name\" %1$s NOT NULL, \"displayName\" %1$s NOT NULL,"
                        + " \"priority\" INTEGER NOT NULL DEFAULT 0, \"isDefault\" BOOLEAN NOT NULL DEFAULT false,"
                        + " \"createdAt\" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" TIMESTAMP(3) NOT NULL, CONSTRAINT \"Role_pkey\" PRIMARY KEY (\"id\"))",
                text));
        sql.execute("CREATE UNIQUE INDEX \"Role_name_key\" ON \"Role\"(\"name\")");
        sql.execute(String.format(
                Locale.ROOT,
                "CREATE TABLE \"User\" (\"id\" %1$s NOT NULL, \"email\" %1$s NOT NULL, \"username\" %1$s NOT NULL,"
                        + " \"password\" %1$s, \"avatar\" %1$s, \"locale\" %1$s NOT NULL DEFAULT 'en',"
                        + " \"currency\" %1$s NOT NULL DEFAULT 'USD', \"emailVerified\" TIMESTAMP(3),"
                        + " \"isBanned\" BOOLEAN NOT NULL DEFAULT false, \"banReason\" %1$s,"
                        + " \"bannedAt\" TIMESTAMP(3), \"isDeleted\" BOOLEAN NOT NULL DEFAULT false,"
                        + " \"deletedAt\" TIMESTAMP(3), \"failedLoginAttempts\" INTEGER NOT NULL DEFAULT 0,"
                        + " \"lastFailedLoginAt\" TIMESTAMP(3), \"lockedUntil\" TIMESTAMP(3),"
                        + " \"createdAt\" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                        + " \"updatedAt\" TIMESTAMP(3) NOT NULL, \"roleId\" %1$s,"
                        + " CONSTRAINT \"User_pkey\" PRIMARY KEY (\"id\"),"
                        + " CONSTRAINT \"User_roleId_fkey\" FOREIGN KEY (\"roleId\") REFERENCES \"Role\"(\"id\")"
                        + " ON DELETE SET NULL ON UPDATE CASCADE)",
                text));
        sql.execute("CREATE UNIQUE INDEX \"User_email_key\" ON \"User\"(\"email\")");
        sql.execute("CREATE UNIQUE INDEX \"User_username_key\" ON \"User\"(\"username\")");
        sql.execute(String.format(
                Locale.ROOT,
                "CREATE TABLE \"UserRole\" (\"id\" %1$s NOT NULL, \"userId\" %1$s NOT NULL, \"roleId\" %1$s NOT NULL,"
                        + " \"grantedAt\" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP, \"expiresAt\" TIMESTAMP(3),"
                        + " \"source\" %1$s NOT NULL DEFAULT 'manual', CONSTRAINT \"UserRole_pkey\" PRIMARY KEY (\"id\"),"
                        + " CONSTRAINT \"UserRole_userId_fkey\" FOREIGN KEY (\"userId\") REFERENCES \"User\"(\"id\")"
                        + " ON DELETE CASCADE ON UPDATE CASCADE,"
                        + " CONSTRAINT \"UserRole_roleId_fkey\" FOREIGN KEY (\"roleId\") REFERENCES \"Role\"(\"id\")"
                        + " ON DELETE CASCADE ON UPDATE CASCADE)",
                text));
        sql.execute("CREATE UNIQUE INDEX \"UserRole_userId_roleId_key\" ON \"UserRole\"(\"userId\", \"roleId\")");
        sql.update(
                "INSERT INTO \"Role\" (\"id\", \"name\", \"displayName\", \"isDefault\", \"updatedAt\")"
                        + " VALUES (?, 'member', 'Member', true, CURRENT_TIMESTAMP)",
                statement -> statement.setString(1, MEMBER_ROLE));
        return database;
    }
}
