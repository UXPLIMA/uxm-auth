package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.auth.application.migrate.ImportAccounts;
import com.uxplima.auth.application.migrate.ImportReport;
import com.uxplima.auth.application.migrate.MarkPremium;
import com.uxplima.auth.application.migrate.PremiumReport;
import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.AuthMeSha256Hasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.LegacyDigestHasher;
import com.uxplima.auth.domain.hash.Verification;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.AuthFixture;
import com.uxplima.auth.testing.InMemoryAccounts;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every layout the plugin ships reads a table made the way its source makes it: a cracked account opens with its
 * old password, a paid account keeps its Mojang id, and an authenticator secret comes across. The hashes were
 * computed with Python's hashlib and bcrypt, for the password "hunter22".
 */
final class ShippedLayoutsTest {

    private static final String SALT = "7f3a9c2e1b5d4f60";
    private static final String DOUBLE_256 = "f7a3536b0757d0ce7ae6025a91557794e8fec6edee523bf8d7799532767fab4a";
    private static final String DOUBLE_512 = "0e0389e51138d62f6787ff6d307d0b9b730fb85a73e1c74ff93a811dacabc52d"
            + "0519d5061ec62659709908b7e0c7a24698ff990f8401869d45b29600188a2349";
    private static final String PLAIN_256 = "20d2fe5e369db54ec7090639a9dc30ec4d608604936239d39e2de07fda09eb0b";
    private static final String BCRYPT = "$2a$04$o34.5HR1UQlklD7vcMNyn.wA2bSW/CXl4NaEwu4OYB2oVqkWXOtkC";
    private static final String SECRET = "JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP";
    private static final UUID STEVE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final Instant SEEN = Instant.parse("2025-06-01T10:00:00Z");
    private static final Map<UUID, String> SERVER_SAW = Map.of(STEVE, "Steve", NOTCH, "Notch", ALEX, "Alex");

    private static final Hashing HASHING = new Hashing(
            new Argon2idHasher(1024, 1, 1),
            List.of(new BcryptHasher(4), new AuthMeSha256Hasher(), new LegacyDigestHasher()));

    @TempDir
    Path folder;

    /** What a column of a source's own table holds for each account. */
    private enum Value {
        NAME,
        LOWER,
        ID,
        UNDASHED,
        PREMIUM,
        PREMIUM_UNDASHED,
        PREMIUM_OR_BLANK,
        FLAG,
        NOT_FLAG,
        PASSWORD,
        SALT,
        TEXT,
        SECRET,
        SECRET_OR_BLANK,
        MILLIS_REGISTERED,
        MILLIS_SEEN,
        STAMP_REGISTERED,
        STAMP_SEEN,
        ADDRESS,
        ZERO
    }

    /** A column of the source's table as the source makes it, written down from its schema, not from the layout. */
    private record Column(String name, String type, Value value, String text) {
        static Column of(String name, String type, Value value) {
            return new Column(name, type, value, "");
        }

        static Column text(String name, String type, String text) {
            return new Column(name, type, Value.TEXT, text);
        }
    }

    /**
     * A source's own table and what the import must bring over from it.
     *
     * @param password "hunter22" as the source writes it
     */
    private record Source(
            String table, String password, List<Column> columns, boolean ids, boolean premium, boolean totp) {}

    private static final Map<String, Source> SOURCES = Map.ofEntries(
            Map.entry(
                    "authme",
                    new Source(
                            "authme",
                            "$SHA$" + SALT + "$" + DOUBLE_256,
                            List.of(
                                    Column.of("username", "VARCHAR(255)", Value.LOWER),
                                            Column.of("realname", "VARCHAR(255)", Value.NAME),
                                    Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                            Column.of("ip", "VARCHAR(40)", Value.ADDRESS),
                                    Column.of("lastlogin", "BIGINT", Value.MILLIS_SEEN),
                                            Column.of("regdate", "BIGINT", Value.MILLIS_REGISTERED),
                                    Column.of("regip", "VARCHAR(40)", Value.ADDRESS),
                                            Column.text("email", "VARCHAR(255)", "your@email.com"),
                                    Column.of("isLogged", "SMALLINT", Value.ZERO),
                                            Column.of("totp", "VARCHAR(32)", Value.SECRET_OR_BLANK)),
                            false,
                            false,
                            true)),
            Map.entry(
                    "nlogin",
                    new Source(
                            "nlogin",
                            "$SHA512$" + DOUBLE_512 + "$" + SALT,
                            List.of(
                                    Column.of("unique_id", "VARCHAR(32)", Value.UNDASHED),
                                    Column.of("mojang_id", "VARCHAR(32)", Value.PREMIUM_UNDASHED),
                                    Column.of("last_name", "VARCHAR(16)", Value.NAME),
                                    Column.of("last_ip", "VARCHAR(40)", Value.ADDRESS),
                                    Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                    Column.of("creation_date", "TIMESTAMP", Value.STAMP_REGISTERED),
                                    Column.of("last_seen", "TIMESTAMP", Value.STAMP_SEEN)),
                            true,
                            true,
                            false)),
            Map.entry(
                    "openlogin",
                    new Source(
                            "openlogin",
                            BCRYPT,
                            List.of(
                                    Column.of("name", "VARCHAR(16)", Value.LOWER),
                                            Column.of("realname", "VARCHAR(16)", Value.NAME),
                                    Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                            Column.of("address", "VARCHAR(40)", Value.ADDRESS),
                                    Column.of("lastlogin", "BIGINT", Value.MILLIS_SEEN),
                                            Column.of("regdate", "BIGINT", Value.MILLIS_REGISTERED)),
                            false,
                            false,
                            false)),
            Map.entry(
                    "librelogin",
                    new Source(
                            "librepremium_data",
                            DOUBLE_256,
                            List.of(
                                    Column.of("uuid", "VARCHAR(36)", Value.ID),
                                    Column.of("premium_uuid", "VARCHAR(36)", Value.PREMIUM),
                                    Column.of("hashed_password", "VARCHAR(255)", Value.PASSWORD),
                                    Column.of("salt", "VARCHAR(255)", Value.SALT),
                                    Column.text("algo", "VARCHAR(255)", "SHA-256"),
                                    Column.of("last_nickname", "VARCHAR(255)", Value.NAME),
                                    Column.of("joined", "TIMESTAMP", Value.STAMP_REGISTERED),
                                    Column.of("last_seen", "TIMESTAMP", Value.STAMP_SEEN),
                                    Column.of("secret", "VARCHAR(255)", Value.SECRET),
                                    Column.of("ip", "VARCHAR(255)", Value.ADDRESS),
                                    Column.text("last_server", "VARCHAR(255)", "lobby")),
                            true,
                            true,
                            true)),
            Map.entry(
                    "limboauth",
                    new Source(
                            "AUTH",
                            "BCRYPT$" + BCRYPT.substring("$2a$".length()),
                            List.of(
                                    Column.of("NICKNAME", "VARCHAR(255)", Value.NAME),
                                            Column.of("LOWERCASENICKNAME", "VARCHAR(255)", Value.LOWER),
                                    Column.of("HASH", "VARCHAR(255)", Value.PASSWORD),
                                            Column.of("IP", "VARCHAR(255)", Value.ADDRESS),
                                    Column.of("TOTPTOKEN", "VARCHAR(255)", Value.SECRET_OR_BLANK),
                                            Column.of("REGDATE", "BIGINT", Value.MILLIS_REGISTERED),
                                    Column.of("UUID", "VARCHAR(255)", Value.ID),
                                            Column.of("PREMIUMUUID", "VARCHAR(255)", Value.PREMIUM_OR_BLANK),
                                    Column.of("LOGINIP", "VARCHAR(255)", Value.ADDRESS),
                                            Column.of("LOGINDATE", "BIGINT", Value.MILLIS_SEEN)),
                            true,
                            true,
                            true)),
            Map.entry(
                    "jpremium",
                    new Source(
                            "user_profiles",
                            "SHA256$" + SALT + "$" + DOUBLE_256,
                            List.of(
                                    Column.of("uniqueId", "VARCHAR(32)", Value.UNDASHED),
                                            Column.of("premiumId", "VARCHAR(32)", Value.PREMIUM_UNDASHED),
                                    Column.of("lastNickname", "VARCHAR(16)", Value.NAME),
                                            Column.of("hashedPassword", "VARCHAR(255)", Value.PASSWORD),
                                    Column.of("firstSeen", "TIMESTAMP", Value.STAMP_REGISTERED),
                                            Column.of("lastSeen", "TIMESTAMP", Value.STAMP_SEEN)),
                            true,
                            true,
                            false)),
            Map.entry(
                    "loginto",
                    new Source(
                            "LoginTo_Players_Accounts",
                            BCRYPT,
                            List.of(
                                    Column.of("uuid", "VARCHAR(36)", Value.ID),
                                            Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                    Column.of("secret", "VARCHAR(255)", Value.SECRET),
                                            Column.of("isPremium", "BOOLEAN", Value.FLAG),
                                    Column.text("isBedrock", "BOOLEAN", "false"),
                                            Column.of("isCracked", "BOOLEAN", Value.NOT_FLAG),
                                    Column.of("joinIp", "VARCHAR(40)", Value.ADDRESS),
                                            Column.of("joinEpoch", "BIGINT", Value.ZERO)),
                            true,
                            true,
                            true)),
            Map.entry(
                    "authy",
                    new Source(
                            "players",
                            PLAIN_256,
                            List.of(
                                    Column.of("uuid", "VARCHAR(36)", Value.ID),
                                            Column.of("username", "VARCHAR(16)", Value.NAME),
                                    Column.of("ip", "VARCHAR(16)", Value.ADDRESS),
                                            Column.of("password", "VARCHAR(64)", Value.PASSWORD),
                                    Column.of("isPinEnabled", "SMALLINT", Value.ZERO),
                                            Column.of("session", "BIGINT", Value.ZERO)),
                            true,
                            false,
                            false)),
            Map.entry(
                    "dynamicbungeeauth",
                    new Source(
                            "playerdata",
                            DOUBLE_512,
                            List.of(
                                    Column.of("uuid", "VARCHAR(36)", Value.ID),
                                    Column.of("name", "VARCHAR(16)", Value.NAME),
                                    Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                    Column.of("salt", "VARCHAR(255)", Value.SALT),
                                    Column.of("premium", "BOOLEAN", Value.FLAG),
                                    Column.of("firstjoin", "TIMESTAMP", Value.STAMP_REGISTERED),
                                    Column.of("lastjoin", "TIMESTAMP", Value.STAMP_SEEN)),
                            true,
                            true,
                            false)),
            Map.entry(
                    "loginsecurity",
                    new Source(
                            "ls_players",
                            BCRYPT,
                            List.of(
                                    Column.of("unique_user_id", "VARCHAR(36)", Value.ID),
                                    Column.of("last_name", "VARCHAR(16)", Value.NAME),
                                    Column.of("password", "VARCHAR(255)", Value.PASSWORD),
                                    Column.text("hashing_algorithm", "INT", "7"),
                                    Column.of("registration_date", "TIMESTAMP", Value.STAMP_REGISTERED),
                                    Column.of("last_login", "TIMESTAMP", Value.STAMP_SEEN)),
                            true,
                            false,
                            false)));

    static Stream<Arguments> layouts(@TempDir Path shippedTo) {
        List<Arguments> cases = new ArrayList<>();
        for (ImportLayout layout : ImportLayout.shipped(shippedTo).values()) {
            if (layout.kind() == ImportLayout.Kind.TABLE) {
                cases.add(Arguments.of(layout.key(), "sqlite", layout));
                cases.add(Arguments.of(layout.key(), "h2", layout));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("layouts")
    void aSourceTableIsImported(String key, String engine, ImportLayout shipped) throws SQLException {
        Source source = Objects.requireNonNull(SOURCES.get(key), "every shipped table layout has its source here");
        ImportLayout layout = shipped.table().isEmpty() ? shipped.onTable(source.table()) : shipped;
        String url = engine.equals("h2")
                ? "jdbc:h2:" + folder.resolve("source").toAbsolutePath()
                : "jdbc:sqlite:" + folder.resolve("source.db");
        String user = engine.equals("h2") ? "sa" : "";
        try (Connection connection = DriverManager.getConnection(url, user, "")) {
            make(connection, source);
            insert(connection, source, "Steve", STEVE, false, false);
            insert(connection, source, "Notch", NOTCH, true, false);
            insert(connection, source, "Alex", ALEX, false, true);
        }
        InMemoryAccounts accounts = new InMemoryAccounts();
        InMemoryFactors factors = new InMemoryFactors();

        ImportReport report = importer(accounts, factors)
                .run(TableImport.open(
                        layout, new TableImport.Address(url, user, ""), id -> Optional.ofNullable(SERVER_SAW.get(id))));

        assertThat(report.imported()).as(report.toString()).isEqualTo(3);
        Account steve = accounts.findByName(new PlayerName("Steve")).orElseThrow();
        assertThat(HASHING.verify(Password.of("hunter22"), Objects.requireNonNull(steve.password())))
                .isInstanceOfAny(Verification.Right.class, Verification.RightButRehash.class);
        assertThat(HASHING.verify(Password.of("hunter23"), Objects.requireNonNull(steve.password())))
                .isInstanceOf(Verification.Wrong.class);
        if (source.columns().stream().anyMatch(column -> column.value() == Value.ADDRESS)) {
            assertThat(steve.lastAddress()).isEqualTo("203.0.113.7");
        }
        if (source.ids()) {
            assertThat(steve.id()).as("the source's own id").isEqualTo(STEVE);
        }
        Account notch = accounts.findByName(new PlayerName("Notch")).orElseThrow();
        assertThat(notch.kind()).isEqualTo(source.premium() ? AccountKind.PREMIUM : AccountKind.CRACKED);
        if (source.premium()) {
            assertThat(notch.premiumId()).isEqualTo(NOTCH);
        }
        Account alex = accounts.findByName(new PlayerName("Alex")).orElseThrow();
        assertThat(factors.app(alex.id()).isPresent())
                .as("the authenticator secret")
                .isEqualTo(source.totp());
        assertThat(factors.app(steve.id())).isEmpty();
    }

    @Test
    void thePremiumListReadsItsSourceTable(@TempDir Path shippedTo) throws SQLException {
        ImportLayout layout =
                Objects.requireNonNull(ImportLayout.shipped(shippedTo).get("fastlogin"));
        String url = "jdbc:sqlite:" + folder.resolve("premium.db");
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            // The table as the source makes it on SQLite, with a cracked row, a premium row and a renamed one.
            statement.execute("CREATE TABLE premium (UserID INTEGER PRIMARY KEY AUTOINCREMENT, UUID CHAR(36),"
                    + " Name VARCHAR(16) NOT NULL, Premium BOOLEAN NOT NULL, LastIp VARCHAR(255) NOT NULL,"
                    + " LastLogin TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, Floodgate INTEGER(3), UNIQUE (Name))");
            statement.execute("INSERT INTO premium (UUID, Name, Premium, LastIp) VALUES (NULL, 'Steve', 0, '')");
            statement.execute("INSERT INTO premium (UUID, Name, Premium, LastIp) VALUES ('"
                    + NOTCH.toString().replace("-", "") + "', 'Notch', 1, '203.0.113.7')");
        }
        InMemoryAccounts accounts = new InMemoryAccounts();
        InMemoryFactors factors = new InMemoryFactors();
        Map<String, Instant> marks = new ConcurrentHashMap<>();
        for (String name : List.of("Steve", "Notch")) {
            accounts.seed(AuthFixture.account(name, HASHING.hash(Password.of("hunter22"))));
        }

        PremiumReport report = new MarkPremium(
                        accounts,
                        marksOf(marks),
                        () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                        name -> new PremiumAnswer.Unknown(),
                        UuidScheme.MOJANG,
                        Clock.fixed(SEEN, ZoneOffset.UTC))
                .run(TableImport.open(layout, new TableImport.Address(url, "", ""), id -> Optional.empty()));

        assertThat(report.marked()).isEqualTo(1);
        Account notch = accounts.findByName(new PlayerName("Notch")).orElseThrow();
        assertThat(notch.kind()).isEqualTo(AccountKind.PREMIUM);
        assertThat(notch.premiumId()).isEqualTo(NOTCH);
        assertThat(accounts.findByName(new PlayerName("Steve")).orElseThrow().kind())
                .isEqualTo(AccountKind.CRACKED);
        assertThat(factors.app(notch.id())).isEmpty();
    }

    private static ImportMarks marksOf(Map<String, Instant> marks) {
        return new ImportMarks() {
            @Override
            public Optional<Instant> completed(String key) {
                return Optional.ofNullable(marks.get(key));
            }

            @Override
            public void complete(String key, Instant at) {
                marks.put(key, at);
            }
        };
    }

    private static ImportAccounts importer(InMemoryAccounts accounts, InMemoryFactors factors) {
        Map<String, Instant> marks = new ConcurrentHashMap<>();
        return new ImportAccounts(
                accounts,
                factors,
                new ImportMarks() {
                    @Override
                    public Optional<Instant> completed(String key) {
                        return Optional.ofNullable(marks.get(key));
                    }

                    @Override
                    public void complete(String key, Instant at) {
                        marks.put(key, at);
                    }
                },
                () -> new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of()),
                UuidScheme.KEEP_FIRST,
                Clock.fixed(SEEN.plusSeconds(86_400), ZoneOffset.UTC));
    }

    private static void make(Connection connection, Source source) throws SQLException {
        List<String> definitions = source.columns().stream()
                .map(column -> column.name() + " " + column.type())
                .toList();
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + source.table() + " (" + String.join(", ", definitions) + ")");
        }
    }

    private static void insert(
            Connection connection, Source source, String name, UUID id, boolean premium, boolean totp)
            throws SQLException {
        List<Column> columns = source.columns();
        String names = String.join(", ", columns.stream().map(Column::name).toList());
        String marks = String.join(", ", columns.stream().map(column -> "?").toList());
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + source.table() + " (" + names + ") VALUES (" + marks + ")")) {
            for (int at = 0; at < columns.size(); at++) {
                bind(statement, at + 1, columns.get(at), source, name, id, premium, totp);
            }
            statement.executeUpdate();
        }
    }

    private static void bind(
            PreparedStatement statement,
            int at,
            Column column,
            Source source,
            String name,
            UUID id,
            boolean premium,
            boolean totp)
            throws SQLException {
        String undashed = id.toString().replace("-", "");
        switch (column.value()) {
            case NAME -> statement.setString(at, name);
            case LOWER -> statement.setString(at, name.toLowerCase(java.util.Locale.ROOT));
            case ID -> statement.setString(at, id.toString());
            case UNDASHED -> statement.setString(at, undashed);
            case PREMIUM -> statement.setString(at, premium ? id.toString() : null);
            case PREMIUM_UNDASHED -> statement.setString(at, premium ? undashed : null);
            case PREMIUM_OR_BLANK -> statement.setString(at, premium ? id.toString() : "");
            case FLAG -> statement.setBoolean(at, premium);
            case NOT_FLAG -> statement.setBoolean(at, !premium);
            case PASSWORD -> statement.setString(at, source.password());
            case SALT -> statement.setString(at, SALT);
            case SECRET -> statement.setString(at, totp ? SECRET : null);
            case SECRET_OR_BLANK -> statement.setString(at, totp ? SECRET : "");
            case MILLIS_REGISTERED ->
                statement.setLong(at, SEEN.minusSeconds(86_400).toEpochMilli());
            case MILLIS_SEEN -> statement.setLong(at, SEEN.toEpochMilli());
            case STAMP_REGISTERED -> statement.setTimestamp(at, Timestamp.from(SEEN.minusSeconds(86_400)));
            case STAMP_SEEN -> statement.setTimestamp(at, Timestamp.from(SEEN));
            case ADDRESS -> statement.setString(at, "203.0.113.7");
            case ZERO -> statement.setInt(at, 0);
            case TEXT -> statement.setString(at, column.text());
        }
    }
}
