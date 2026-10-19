package com.uxplima.auth.adapter.outbound;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.uxmlib.storage.StorageException;
import com.uxplima.uxmlib.storage.sql.SelectBuilder;
import org.jspecify.annotations.Nullable;

/**
 * Reads another login plugin's table as its layout describes it.
 *
 * <p>The source is only read: its connection is switched to read-only before anything is sent, and the one
 * statement sent is a {@code SELECT} of the layout's columns, made by uxmLib's builder from names the layout
 * checked. The whole table is read in that one statement and handed over afterwards.
 */
public final class TableImport implements ImportSource {

    /** Opens a connection to the source. */
    @FunctionalInterface
    public interface Opener {
        Connection open() throws SQLException;
    }

    /** Where the source is, and the login it takes. The password is never printed. */
    public record Address(String jdbcUrl, String username, String password) {
        public Address {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
        }

        @Override
        public String toString() {
            return "Address[jdbcUrl=" + jdbcUrl + ", username=" + username + ", password=hidden]";
        }
    }

    private final ImportLayout layout;
    private final Opener opener;
    private final Function<UUID, Optional<String>> names;

    /** @param names the name the server knows a player's id by, for a source that keeps no names */
    public TableImport(ImportLayout layout, Opener opener, Function<UUID, Optional<String>> names) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.names = Objects.requireNonNull(names, "names");
        if (layout.kind() != ImportLayout.Kind.TABLE || layout.table().isEmpty()) {
            throw new IllegalArgumentException(layout.display() + " is read from a table, and import.table names none");
        }
    }

    /** A reader over the source at this address, its driver loaded from the ones the jar carries. */
    public static TableImport open(ImportLayout layout, Address address, Function<UUID, Optional<String>> names) {
        AuthDatabase.load(AuthDatabase.driverOf(address.jdbcUrl()));
        return new TableImport(layout, () -> connect(address), names);
    }

    /** A connection to the source, opened read-only where the driver only allows that at the start. */
    static Connection connect(Address address) throws SQLException {
        Properties login = new Properties();
        login.setProperty("user", address.username());
        login.setProperty("password", address.password());
        // SQLite takes read-only as an open flag, never later.
        login.setProperty("open_mode", "1");
        String url = AuthDatabase.addressOf(address.jdbcUrl());
        // H2 is read-only only by its address; a read-only hint later changes nothing there.
        return DriverManager.getConnection(url.startsWith("jdbc:h2:") ? url + ";ACCESS_MODE_DATA=r" : url, login);
    }

    @Override
    public String key() {
        return layout.key();
    }

    @Override
    public Stream<ImportedAccount> read() {
        String select = SelectBuilder.from(layout.table())
                .columns(layout.columns().named().toArray(String[]::new))
                .build()
                .sql();
        List<ImportedAccount> rows = new ArrayList<>();
        try (Connection connection = opener.open()) {
            if (!connection.isReadOnly()) {
                connection.setReadOnly(true);
            }
            if (!connection.isReadOnly()) {
                throw new SQLException("the connection would not turn read-only");
            }
            try (PreparedStatement statement = connection.prepareStatement(select);
                    ResultSet found = statement.executeQuery()) {
                while (found.next()) {
                    row(found).ifPresent(rows::add);
                }
            }
        } catch (SQLException failed) {
            throw new StorageException(layout.display() + "'s table " + layout.table() + " could not be read", failed);
        }
        return rows.stream();
    }

    /** One account, or nothing for a row with neither a name nor an id the server knows. */
    private Optional<ImportedAccount> row(ResultSet found) throws SQLException {
        ImportLayout.Columns columns = layout.columns();
        UUID id = id(text(found, columns.id()));
        String name = text(found, columns.name());
        if (name == null && id != null) {
            name = names.apply(id).orElse(null);
        }
        if (name == null) {
            name = id == null ? "" : id.toString();
        }
        return Optional.of(new ImportedAccount(
                name,
                id,
                premiumId(found, id),
                password(found),
                text(found, columns.totp()),
                time(found, columns.registered()),
                time(found, columns.lastLogin()),
                text(found, columns.address())));
    }

    private @Nullable UUID premiumId(ResultSet found, @Nullable UUID id) throws SQLException {
        ImportLayout.Columns columns = layout.columns();
        if (!columns.premiumId().isEmpty()) {
            return id(text(found, columns.premiumId()));
        }
        if (!columns.premiumFlag().isEmpty() && found.getBoolean(columns.premiumFlag())) {
            return id;
        }
        return null;
    }

    private LegacyHashes.@Nullable Read password(ResultSet found) throws SQLException {
        ImportLayout.Columns columns = layout.columns();
        String raw = text(found, columns.password());
        if (raw == null) {
            return null;
        }
        return LegacyHashes.rewrite(
                layout.hashStyle(), raw, text(found, columns.salt()), text(found, columns.algorithm()));
    }

    private static @Nullable String text(ResultSet found, String column) throws SQLException {
        if (column.isEmpty()) {
            return null;
        }
        String value = found.getString(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private @Nullable UUID id(@Nullable String written) {
        if (written == null || layout.idStyle() == ImportLayout.IdStyle.NONE) {
            return null;
        }
        String dashed = written.length() == 32
                ? written.replaceFirst(
                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                        "$1-$2-$3-$4-$5")
                : written;
        try {
            return UUID.fromString(dashed);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private @Nullable Instant time(ResultSet found, String column) throws SQLException {
        if (column.isEmpty()) {
            return null;
        }
        return switch (layout.timeStyle()) {
            case NONE -> null;
            case MILLIS -> positive(found.getLong(column), 1);
            case SECONDS -> positive(found.getLong(column), 1000);
            case TIMESTAMP -> {
                Timestamp at = found.getTimestamp(column);
                yield at == null || at.getTime() <= 0 ? null : at.toInstant();
            }
        };
    }

    private static @Nullable Instant positive(long value, long toMillis) {
        return value <= 0 ? null : Instant.ofEpochMilli(value * toMillis);
    }

    /** Nothing is held open between reads. */
    @Override
    public void close() {}
}
