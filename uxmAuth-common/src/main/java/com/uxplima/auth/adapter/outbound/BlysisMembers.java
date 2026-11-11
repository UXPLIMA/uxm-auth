package com.uxplima.auth.adapter.outbound;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.storage.sql.TxSql;
import org.jspecify.annotations.Nullable;

/**
 * The members of a Blysis website, in its own database: the {@code "User"} table, and the {@code "Role"} and
 * {@code "UserRole"} tables a member made here is entered in.
 *
 * <p>The names are Prisma's, which PostgreSQL keeps in the case they were written in, so every one is quoted. Every
 * value is bound. A username is found without regard to case, as the website finds it at its sign in. Blysis keeps
 * its times as {@code TIMESTAMP(3)} in UTC with no zone, so they are written and read as UTC wall times.
 *
 * <p>Nothing here catches a failure: a database that does not answer throws, so a lookup that failed is never
 * taken for a name nobody holds.
 */
final class BlysisMembers {

    private static final String COLUMNS =
            "\"id\", \"username\", \"password\", \"isBanned\", \"isDeleted\", \"createdAt\"";
    private static final String BY_NAME = "SELECT " + COLUMNS + " FROM \"User\" WHERE LOWER(\"username\") = LOWER(?)";
    private static final String BY_ID = "SELECT " + COLUMNS + " FROM \"User\" WHERE \"id\" = ?";
    private static final String ROLE = "SELECT \"id\" FROM \"Role\" WHERE \"name\" = ?";
    private static final String ADD = "INSERT INTO \"User\" (\"id\", \"email\", \"username\", \"password\", \"locale\","
            + " \"currency\", \"createdAt\", \"updatedAt\", \"roleId\") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    private static final String ADD_ROLE =
            "INSERT INTO \"UserRole\" (\"id\", \"userId\", \"roleId\", \"source\") VALUES (?, ?, ?, ?)";
    private static final String REPLACE = "UPDATE \"User\" SET \"password\" = ?, \"updatedAt\" = ?"
            + " WHERE \"id\" = ? AND \"password\" = ? AND \"isDeleted\" = FALSE";
    private static final String WRITE =
            "UPDATE \"User\" SET \"password\" = ?, \"updatedAt\" = ? WHERE \"id\" = ? AND \"isDeleted\" = FALSE";
    private static final String TABLES = "SELECT " + COLUMNS + " FROM \"User\" WHERE 1 = 0";

    /** What a member made here says made it, in {@code "UserRole".source}. */
    static final String SOURCE = "uxmauth";

    /** The mark a member id made here starts with, so a reader of the table can tell where it came from. */
    private static final String ID_PREFIX = "uxm";

    private static final char[] ID_LETTERS = "0123456789abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int ID_LENGTH = 22;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A member as the website keeps them.
     *
     * @param password the hash, or null or empty for a member who signs in some other way, or whose account went
     */
    record Member(
            String id,
            String username,
            @Nullable String password,
            boolean banned,
            boolean deleted,
            @Nullable Instant createdAt) {

        Member {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(username, "username");
        }

        /** Whether the member has a password a login can be checked against. */
        boolean hasPassword() {
            return !deleted && password != null && !password.isEmpty();
        }

        /** Whether the member may not log in, here or on the website. */
        boolean closed() {
            return banned || deleted;
        }
    }

    /**
     * What a username holds on the website.
     *
     * @param member the member the name opens: the one in exactly this case, or the only one in any case
     * @param taken whether any member holds the name in any case
     */
    record Named(Optional<Member> member, boolean taken) {
        Named {
            Objects.requireNonNull(member, "member");
        }
    }

    /** What the website gives a member made here. */
    record Joining(String email, String locale, String currency, String role) {
        Joining {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(role, "role");
        }
    }

    private final Database database;

    BlysisMembers(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    /**
     * Whether the tables are there and the role is, read once at the start. A missing table is refused; a missing
     * role is said, and members made here are then made with none.
     *
     * @return what the operator should hear, if anything
     */
    Optional<String> check(String role) {
        database.inTransaction(sql -> sql.query(TABLES, statement -> {}, BlysisMembers::read));
        if (roleId(role).isEmpty()) {
            return Optional.of("blysis.member-role is " + role + ", and the website has no role of that name: a"
                    + " member who registers in game gets no role until it is made on the website.");
        }
        return Optional.empty();
    }

    /**
     * The member under this name. Two members whose names differ only in case, from before the website held a name
     * to one person, are told apart by the exact spelling; a spelling that matches more than one and none exactly
     * opens nothing, as the website's own sign in does.
     */
    Named named(PlayerName name) {
        List<Member> found = database.transaction(sql -> byName(sql, name.value()));
        Optional<Member> exact = found.stream()
                .filter(member -> member.username().equals(name.value()))
                .findFirst();
        Optional<Member> member = exact.isPresent() || found.size() != 1 ? exact : Optional.of(found.getFirst());
        return new Named(member, !found.isEmpty());
    }

    Optional<Member> byId(String id) {
        return database.transaction(
                sql -> sql.queryFirst(BY_ID, statement -> statement.setString(1, id), BlysisMembers::read));
    }

    /**
     * Makes a member under this name with this password, entered in the role, in one transaction. Empty when the
     * website holds the name in any case, and nothing is written.
     *
     * @return the new member's id
     */
    Optional<String> add(PlayerName name, PasswordHash password, Joining joining, Instant at) {
        String id = freshId();
        return database.transaction(sql -> {
            if (!byName(sql, name.value()).isEmpty()) {
                return Optional.<String>empty();
            }
            Optional<String> role =
                    sql.queryFirst(ROLE, statement -> statement.setString(1, joining.role()), row -> row.getString(1));
            sql.update(ADD, statement -> {
                statement.setString(1, id);
                statement.setString(2, joining.email());
                statement.setString(3, name.value());
                statement.setString(4, password.encoded());
                statement.setString(5, joining.locale());
                statement.setString(6, joining.currency());
                setTime(statement, 7, at);
                setTime(statement, 8, at);
                if (role.isPresent()) {
                    statement.setString(9, role.get());
                } else {
                    statement.setNull(9, Types.VARCHAR);
                }
            });
            if (role.isPresent()) {
                sql.update(ADD_ROLE, statement -> {
                    statement.setString(1, freshId());
                    statement.setString(2, id);
                    statement.setString(3, role.get());
                    statement.setString(4, SOURCE);
                });
            }
            return Optional.of(id);
        });
    }

    /** Replaces the password only while the member still holds the expected one. */
    boolean replace(String id, String expected, PasswordHash fresh, Instant at) {
        return database.transaction(sql -> sql.update(REPLACE, statement -> {
                    statement.setString(1, fresh.encoded());
                    setTime(statement, 2, at);
                    statement.setString(3, id);
                    statement.setString(4, expected);
                }))
                == 1;
    }

    /** Writes the password, whatever the member held, for staff who set it. */
    boolean write(String id, PasswordHash fresh, Instant at) {
        return database.transaction(sql -> sql.update(WRITE, statement -> {
                    statement.setString(1, fresh.encoded());
                    setTime(statement, 2, at);
                    statement.setString(3, id);
                }))
                == 1;
    }

    private Optional<String> roleId(String role) {
        return database.transaction(
                sql -> sql.queryFirst(ROLE, statement -> statement.setString(1, role), row -> row.getString(1)));
    }

    private static List<Member> byName(TxSql sql, String name) {
        return sql.query(BY_NAME, statement -> statement.setString(1, name), BlysisMembers::read);
    }

    private static Member read(ResultSet row) throws SQLException {
        LocalDateTime created = row.getObject("createdAt", LocalDateTime.class);
        return new Member(
                row.getString("id"),
                row.getString("username"),
                row.getString("password"),
                row.getBoolean("isBanned"),
                row.getBoolean("isDeleted"),
                created == null ? null : created.toInstant(ZoneOffset.UTC));
    }

    private static void setTime(PreparedStatement statement, int column, Instant at) throws SQLException {
        statement.setObject(column, LocalDateTime.ofInstant(at, ZoneOffset.UTC));
    }

    /** A fresh id in the letters Blysis' own ids use, long enough that two never meet. */
    private static String freshId() {
        StringBuilder id = new StringBuilder(ID_PREFIX);
        for (int at = 0; at < ID_LENGTH; at++) {
            id.append(ID_LETTERS[RANDOM.nextInt(ID_LETTERS.length)]);
        }
        return id.toString();
    }
}
