package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.uxmlib.config.HoconConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A layout file says where another plugin keeps its accounts, and one reader reads any of them, never writing. */
final class TableImportTest {

    private static final UUID STEVE = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String PLAIN_256 = "20d2fe5e369db54ec7090639a9dc30ec4d608604936239d39e2de07fda09eb0b";

    @TempDir
    Path folder;

    private ImportLayout layout(String body) throws IOException {
        Path file = folder.resolve("layout.conf");
        Files.writeString(file, body);
        return ImportLayout.read("test", HoconConfig.load(file));
    }

    private String url(String engine) {
        return engine.equals("h2")
                ? "jdbc:h2:" + folder.resolve("source").toAbsolutePath()
                : "jdbc:sqlite:" + folder.resolve("source.db");
    }

    private void source(String url, String... statements) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, url.startsWith("jdbc:h2") ? "sa" : "", "");
                Statement statement = connection.createStatement()) {
            for (String sql : statements) {
                statement.execute(sql);
            }
        }
    }

    private List<ImportedAccount> read(ImportLayout layout, String url) {
        try (TableImport source = TableImport.open(
                layout,
                new TableImport.Address(url, url.startsWith("jdbc:h2") ? "sa" : "", ""),
                id -> Optional.of(id.equals(STEVE) ? "Steve" : "Unknown"))) {
            return source.read().toList();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("columns, undashed ids, a premium id, milliseconds and a plain hash are read as the layout says")
    void aLayoutIsRead(String engine) throws Exception {
        String url = url(engine);
        source(
                url,
                "CREATE TABLE accounts (nick VARCHAR(16), uid VARCHAR(32), mojang VARCHAR(32), pass VARCHAR(64),"
                        + " secret VARCHAR(64), joined BIGINT, seen BIGINT, addr VARCHAR(40))",
                "INSERT INTO accounts VALUES ('Steve', '" + STEVE.toString().replace("-", "") + "', NULL, '" + PLAIN_256
                        + "', 'JBSWY3DPEHPK3PXP', 1700000000000, 1700000600000, '203.0.113.7')",
                "INSERT INTO accounts VALUES ('Notch', '" + NOTCH.toString().replace("-", "") + "', '"
                        + NOTCH.toString().replace("-", "") + "', NULL, NULL, 0, 0, NULL)");
        ImportLayout layout = layout("""
                display = "Test"
                table = "accounts"
                id-style = undashed
                time-style = millis
                hash-style = plain
                columns { name = nick, id = uid, premium-id = mojang, password = pass, totp = secret,
                  registered = joined, last-login = seen, address = addr }
                """);

        List<ImportedAccount> rows = read(layout, url);

        assertThat(rows).hasSize(2);
        ImportedAccount steve = rows.get(0);
        assertThat(steve.name()).isEqualTo("Steve");
        assertThat(steve.id()).isEqualTo(STEVE);
        assertThat(steve.premiumId()).isNull();
        assertThat(steve.password()).isInstanceOf(LegacyHashes.Known.class);
        assertThat(steve.totpSecret()).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(steve.registeredAt()).isEqualTo(Instant.ofEpochMilli(1700000000000L));
        assertThat(steve.lastLogin()).isEqualTo(Instant.ofEpochMilli(1700000600000L));
        assertThat(steve.lastAddress()).isEqualTo("203.0.113.7");
        ImportedAccount notch = rows.get(1);
        assertThat(notch.premiumId()).isEqualTo(NOTCH);
        assertThat(notch.password()).isNull();
        assertThat(notch.registeredAt()).as("a zero date is no date").isNull();
    }

    @Test
    @DisplayName("a premium flag, timestamps, dashed ids and a name taken from the server's record of the id")
    void flagsTimestampsAndNamesFromIds() throws Exception {
        String url = url("sqlite");
        source(
                url,
                "CREATE TABLE players (uuid VARCHAR(36), pw VARCHAR(64), premium BOOLEAN, first TIMESTAMP)",
                "INSERT INTO players VALUES ('" + STEVE + "', '" + PLAIN_256 + "', 1, NULL)");
        ImportLayout layout = layout("""
                display = "Test"
                table = "players"
                id-style = dashed
                time-style = timestamp
                hash-style = plain
                columns { id = uuid, password = pw, premium-flag = premium, registered = first }
                """);

        ImportedAccount steve = read(layout, url).getFirst();

        assertThat(steve.name()).isEqualTo("Steve");
        assertThat(steve.premiumId()).as("the flag says the id is Mojang's").isEqualTo(STEVE);
        assertThat(steve.registeredAt()).isNull();
    }

    @Test
    @DisplayName(
            "a layout naming a column twice, an unknown style or a name that is not an identifier is refused by key")
    void aBadLayoutIsRefused() {
        assertThatThrownBy(() -> layout("table = \"users;drop\"\nhash-style = plain\ncolumns { name = a }\n"))
                .hasMessageContaining("table");
        assertThatThrownBy(() -> layout("table = t\nhash-style = md9\ncolumns { name = a }\n"))
                .hasMessageContaining("hash-style");
        assertThatThrownBy(() -> layout("table = t\nhash-style = plain\nid-style = sideways\ncolumns { name = a }\n"))
                .hasMessageContaining("id-style");
        assertThatThrownBy(() -> layout("table = t\nhash-style = plain\ncolumns { password = p }\n"))
                .as("a layout with no name and no id cannot name an account")
                .hasMessageContaining("columns.name");
    }

    @Test
    @DisplayName("the source is opened read-only and is sent nothing but one SELECT")
    void theSourceIsOnlyRead() throws Exception {
        String url = url("sqlite");
        source(url, "CREATE TABLE t (n VARCHAR(16), p VARCHAR(64))", "INSERT INTO t VALUES ('Steve', 'x')");
        ImportLayout layout = layout("table = t\nhash-style = plain\ncolumns { name = n, password = p }\n");
        List<String> sent = new CopyOnWriteArrayList<>();
        List<Boolean> readOnly = new CopyOnWriteArrayList<>();

        try (TableImport source =
                new TableImport(layout, () -> recording(url, sent, readOnly), id -> Optional.empty())) {
            assertThat(source.read().toList()).hasSize(1);
        }

        assertThat(readOnly).isNotEmpty().containsOnly(true);
        assertThat(sent).singleElement().asString().startsWith("SELECT ");
    }

    /** A connection to the source that writes down every statement prepared on it and every read-only switch. */
    private static Connection recording(String url, List<String> sent, List<Boolean> readOnly) {
        try {
            Connection real = TableImport.connect(new TableImport.Address(url, "", ""));
            return (Connection) Proxy.newProxyInstance(
                    TableImportTest.class.getClassLoader(),
                    new Class<?>[] {Connection.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "prepareStatement", "createStatement", "prepareCall", "nativeSQL" ->
                                sent.add(args == null ? method.getName() : String.valueOf(args[0]));
                            default -> {}
                        }
                        Object answer = method.invoke(real, args);
                        if (method.getName().equals("isReadOnly")) {
                            readOnly.add((Boolean) answer);
                        }
                        return answer;
                    });
        } catch (SQLException failed) {
            throw new IllegalStateException(failed);
        }
    }

    @Test
    @DisplayName("the layouts the plugin ships are all read without a refusal")
    void theShippedLayoutsRead() throws IOException {
        for (Map.Entry<String, ImportLayout> shipped :
                ImportLayout.shipped(folder.resolve("imports")).entrySet()) {
            assertThat(shipped.getValue().display()).as(shipped.getKey()).isNotBlank();
        }
        assertThat(ImportLayout.shipped(folder.resolve("imports"))).isNotEmpty();
    }

    @Test
    @DisplayName("a source address this jar has no driver for is refused under the import's own key")
    void anUnknownDriverNamesTheImportKey() throws IOException {
        ImportLayout layout = layout("table = t\nhash-style = plain\ncolumns { name = n, password = p }\n");

        assertThatThrownBy(() -> TableImport.open(
                        layout, new TableImport.Address("jdbc:oracle:thin:@db", "", ""), id -> Optional.empty()))
                .hasMessageStartingWith("import.jdbc-url starts with jdbc:oracle");
        assertThatThrownBy(() -> TableImport.open(
                        layout, new TableImport.Address("accounts.db", "", ""), id -> Optional.empty()))
                .hasMessageStartingWith("import.jdbc-url is not a JDBC address");
    }

    @Test
    @DisplayName("a premium list names a premium column, and is read from a table like any other source")
    void aPremiumListNamesAPremiumColumn() throws IOException, SQLException {
        assertThatThrownBy(() -> layout("kind = premium\ntable = t\ncolumns { name = n }\n"))
                .hasMessageContaining("premium-flag");
        ImportLayout layout = layout("""
                kind = premium
                table = premium
                id-style = undashed
                columns { name = Name, id = UUID, premium-flag = Premium }
                """);
        String url = url("sqlite");
        source(
                url,
                "CREATE TABLE premium (UserID INTEGER, UUID CHAR(36), Name VARCHAR(16), Premium BOOLEAN)",
                "INSERT INTO premium VALUES (1, '069a79f444e94726a5befca90e38aaf5', 'Notch', 1)",
                "INSERT INTO premium VALUES (2, NULL, 'Steve', 0)");

        assertThat(read(layout, url))
                .extracting(ImportedAccount::name, ImportedAccount::premiumId)
                .containsExactlyInAnyOrder(
                        tuple("Notch", UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5")), tuple("Steve", null));
    }
}
