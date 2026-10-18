package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.LogIn;
import com.uxplima.auth.application.LoginResult;
import com.uxplima.auth.application.Sessions;
import com.uxplima.auth.application.lane.HashingLane;
import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.account.Account;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHash;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.password.Password;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.domain.state.Attempts;
import com.uxplima.auth.domain.state.AuthState;
import com.uxplima.uxmlib.storage.sql.Database;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A website's login table beside the plugin's own, on SQLite and on H2: a password changed on the website works at
 * the next login, one changed in game is written in the website's format, and an account the website made logs in.
 */
final class WebsiteAccountsTest {

    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final WebsiteTable.Columns COLUMNS =
            new WebsiteTable.Columns("username", "realname", "password", "ip", "lastlogin", "regdate", "regip");

    @TempDir
    Path folder;

    private final HashingLane lane = new HashingLane(1, 1, 16);
    private @Nullable Database opened;

    private Database open(String engine) {
        String url = engine.equals("h2")
                ? "jdbc:h2:" + folder.resolve("h2").toAbsolutePath()
                : "jdbc:sqlite:" + folder.resolve("accounts.db");
        opened = AuthDatabase.open(new PluginSettings.Storage(url, engine.equals("h2") ? "sa" : "", "", 2), "site");
        return opened;
    }

    private Database database() {
        return Objects.requireNonNull(opened, "open a database first");
    }

    @AfterEach
    void close() {
        lane.close();
        if (opened != null) {
            opened.close();
        }
    }

    private WebsiteAccounts accounts(WebsiteTable.Hash hash) {
        return WebsiteAccounts.over(
                new SqlAccountRepository(database()),
                database(),
                new WebsiteTable(true, "authme", hash, COLUMNS),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static Hashing hashing(WebsiteTable.Hash hash) {
        return new Hashing(hash.hasher(), List.of(new Argon2idHasher(1024, 1, 1)));
    }

    private Account steve(Hashing hashing) {
        return new Account(
                UuidScheme.offline(new PlayerName("Steve")),
                new PlayerName("Steve"),
                AccountKind.CRACKED,
                null,
                hashing.hash(Password.of("hunter22")),
                NOW,
                "203.0.113.7",
                NOW,
                "203.0.113.7");
    }

    private Optional<String> sitePassword(String name) {
        return database()
                .transaction(sql -> sql.queryFirst(
                        "SELECT password FROM authme WHERE username = ?",
                        statement -> statement.setString(1, name),
                        row -> row.getString("password")));
    }

    private LoginResult logIn(AccountRepository accounts, Hashing hashing, String name, String password) {
        Account found = accounts.findByName(new PlayerName(name)).orElseThrow();
        Sessions sessions = new Sessions();
        sessions.open(found.id(), new AuthState.Registered(found, Attempts.fresh(3)));
        return new LogIn(accounts, hashing, lane, sessions, Clock.fixed(NOW, ZoneOffset.UTC))
                .run(found.id(), Password.of(password), "203.0.113.7")
                .join();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a password changed on the website works at the next login, and the old one does not")
    void aWebsiteChangeWorksAtTheNextLogin(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        accounts.insert(steve(hashing));
        String changed = hashing.hash(Password.of("fresh777")).encoded();
        database()
                .inTransaction(sql -> sql.update("UPDATE authme SET password = ? WHERE username = ?", statement -> {
                    statement.setString(1, changed);
                    statement.setString(2, "steve");
                }));

        assertThat(accounts.findByName(new PlayerName("STEVE")).orElseThrow().password())
                .as("the name a connection reads its account by")
                .isEqualTo(new PasswordHash(changed));
        assertThat(logIn(accounts, hashing, "Steve", "hunter22")).isInstanceOf(LoginResult.Wrong.class);
        assertThat(logIn(accounts, hashing, "Steve", "fresh777")).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a password changed in game is written to the website in its format, and a change made on the"
            + " website meanwhile wins")
    void aGameChangeReachesTheWebsite(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account steve = steve(hashing);
        accounts.insert(steve);
        PasswordHash fresh = hashing.hash(Password.of("fresh777"));

        assertThat(accounts.replacePassword(steve.id(), steve.password(), fresh))
                .isTrue();
        assertThat(sitePassword("steve")).contains(fresh.encoded());
        assertThat(fresh.encoded()).startsWith("$SHA$");
        assertThat(new SqlAccountRepository(database())
                        .findById(steve.id())
                        .orElseThrow()
                        .password())
                .isEqualTo(fresh);
        assertThat(accounts.replacePassword(steve.id(), steve.password(), hashing.hash(Password.of("other888"))))
                .as("the expected value is the one the website holds now")
                .isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("an account the website made logs in, and the plugin's own row is made then")
    void aWebsiteAccountLogsIn(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        String made = hashing.hash(Password.of("webpass1")).encoded();
        database()
                .inTransaction(sql -> sql.update(
                        "INSERT INTO authme (username, realname, password, ip, lastlogin, regdate, regip)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                        statement -> {
                            statement.setString(1, "alex");
                            statement.setString(2, "Alex");
                            statement.setString(3, made);
                            statement.setString(4, "198.51.100.4");
                            statement.setLong(5, 0);
                            statement.setLong(6, NOW.minusSeconds(3600).toEpochMilli());
                            statement.setString(7, "198.51.100.4");
                        }));
        assertThat(new SqlAccountRepository(database()).findByName(new PlayerName("alex")))
                .isEmpty();

        assertThat(logIn(accounts, hashing, "alex", "webpass1")).isInstanceOf(LoginResult.LoggedIn.class);

        Account adopted = new SqlAccountRepository(database())
                .findByName(new PlayerName("alex"))
                .orElseThrow();
        assertThat(adopted.id()).isEqualTo(UuidScheme.offline(new PlayerName("Alex")));
        assertThat(adopted.name().value()).isEqualTo("Alex");
        assertThat(adopted.kind()).isEqualTo(AccountKind.CRACKED);
        assertThat(adopted.unverified())
                .as("the premium source is asked about the name at the next connection, as for a registration")
                .isTrue();
        assertThat(adopted.registeredAt()).isEqualTo(NOW.minusSeconds(3600));

        madeOnTheWebsite("bob", "Bob", hashing.hash(Password.of("webpass1")).encoded());
        assertThat(accounts.findByName(new PlayerName("Bob"))).isPresent();
        String changed = hashing.hash(Password.of("webpass2")).encoded();
        database()
                .inTransaction(sql -> sql.update("UPDATE authme SET password = ? WHERE username = ?", statement -> {
                    statement.setString(1, changed);
                    statement.setString(2, "bob");
                }));
        assertThat(logIn(accounts, hashing, "Bob", "webpass2"))
                .as("an adopted row stays the account's when the website changes it before the first login")
                .isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"SHA256", "BCRYPT", "PBKDF2", "ARGON2"})
    @DisplayName("every format is written to the website as the website writes it, and read back from there")
    void everyFormatIsWritten(String format) {
        open("sqlite");
        WebsiteTable.Hash hash = WebsiteTable.Hash.valueOf(format);
        Hashing hashing = hashing(hash);
        WebsiteAccounts accounts = accounts(hash);
        accounts.insert(steve(hashing));

        String stored = sitePassword("steve").orElseThrow();
        assertThat(stored)
                .matches(
                        switch (hash) {
                            case SHA256 -> "\\$SHA\\$[0-9a-f]{16}\\$[0-9a-f]{64}";
                            case BCRYPT -> "\\$2a\\$10\\$.{53}";
                            case PBKDF2 -> "pbkdf2_sha256\\$10000\\$[0-9a-f]{16}\\$[0-9A-F]{128}";
                            case ARGON2 -> "\\$argon2i\\$v=19\\$m=65536,t=2,p=1\\$.{22}\\$.{43}";
                        });
        assertThat(logIn(accounts, hashing, "Steve", "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a login, a rename and a removal reach the website's row; a premium account without a password"
            + " writes no row")
    void theRowFollowsTheAccount(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account steve = steve(hashing);
        accounts.insert(steve);

        accounts.recordLogin(steve.id(), NOW.plusSeconds(60), "198.51.100.9");
        Optional<String> seen = database()
                .transaction(sql -> sql.queryFirst(
                        "SELECT ip, lastlogin FROM authme WHERE username = ?",
                        statement -> statement.setString(1, "steve"),
                        row -> row.getString("ip") + " " + row.getLong("lastlogin")));
        assertThat(seen).contains("198.51.100.9 " + NOW.plusSeconds(60).toEpochMilli());

        accounts.rename(steve.id(), new PlayerName("Steven"), NOW);
        assertThat(sitePassword("steve")).isEmpty();
        assertThat(sitePassword("steven")).isPresent();

        accounts.delete(steve.id());
        assertThat(sitePassword("steven")).isEmpty();

        Account notch = new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                AccountKind.PREMIUM,
                UUID.randomUUID(),
                null,
                NOW,
                "a",
                null,
                null);
        accounts.insert(notch);
        assertThat(sitePassword("notch")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("an account the plugin held before the table was turned on reaches it at its next login or its"
            + " next password")
    void anOlderAccountReachesTheWebsite(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        SqlAccountRepository before = new SqlAccountRepository(database());
        Account steve = steve(hashing);
        before.insert(steve);
        Account alex = new Account(
                UuidScheme.offline(new PlayerName("Alex")),
                new PlayerName("Alex"),
                AccountKind.CRACKED,
                null,
                hashing.hash(Password.of("alexpass1")),
                NOW,
                "198.51.100.4",
                null,
                null);
        before.insert(alex);
        assertThat(sitePassword("steve")).isEmpty();

        assertThat(logIn(accounts, hashing, "Steve", "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
        assertThat(sitePassword("steve"))
                .contains(Objects.requireNonNull(steve.password()).encoded());

        PasswordHash fresh = hashing.hash(Password.of("fresh777"));
        assertThat(accounts.replacePassword(alex.id(), alex.password(), fresh)).isTrue();
        assertThat(sitePassword("alex")).contains(fresh.encoded());
    }

    private void madeOnTheWebsite(String name, String realName, String password) {
        database()
                .inTransaction(sql -> sql.update(
                        "INSERT INTO authme (username, realname, password, regdate) VALUES (?, ?, ?, ?)", statement -> {
                            statement.setString(1, name);
                            statement.setString(2, realName);
                            statement.setString(3, password);
                            statement.setLong(4, NOW.toEpochMilli());
                        }));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a website row under a premium or Bedrock player's name gives that account no password, and"
            + " removing or renaming the account leaves the website's row alone")
    void aPaidAccountTakesNothingFromTheWebsite(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account notch = new Account(
                UUID.randomUUID(),
                new PlayerName("Notch"),
                AccountKind.PREMIUM,
                UUID.randomUUID(),
                null,
                NOW,
                "a",
                null,
                null);
        Account bedrock = new Account(
                UUID.randomUUID(), new PlayerName(".Herobrine"), AccountKind.BEDROCK, null, null, NOW, "a", null, null);
        accounts.insert(notch);
        accounts.insert(bedrock);
        String planted = hashing.hash(Password.of("planted1")).encoded();
        madeOnTheWebsite("notch", "Notch", planted);
        madeOnTheWebsite(".herobrine", ".Herobrine", planted);

        assertThat(accounts.findByName(new PlayerName("Notch")).orElseThrow().password())
                .isNull();
        assertThat(accounts.findById(notch.id()).orElseThrow().password()).isNull();
        assertThat(accounts.findByPremiumId(java.util.Objects.requireNonNull(notch.premiumId()))
                        .orElseThrow()
                        .password())
                .isNull();
        assertThat(accounts.findByName(new PlayerName(".Herobrine"))
                        .orElseThrow()
                        .password())
                .isNull();

        accounts.recordLogin(notch.id(), NOW, "198.51.100.9");
        accounts.rename(bedrock.id(), new PlayerName(".Steve"), NOW);
        accounts.delete(notch.id());
        assertThat(sitePassword("notch")).contains(planted);
        assertThat(sitePassword(".herobrine")).contains(planted);
        assertThat(sitePassword(".steve")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a registration in game under a name the website took meanwhile is refused, and the website's"
            + " password stays")
    void aRegistrationNeverOverwritesTheWebsite(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        String theirs = hashing.hash(Password.of("webpass1")).encoded();
        madeOnTheWebsite("steve", "STEVE", theirs);

        assertThat(accounts.insert(steve(hashing)))
                .as("named as the website wrote it")
                .isEqualTo(new AccountRepository.NameTaken(new PlayerName("STEVE")));
        assertThat(sitePassword("steve")).contains(theirs);
        assertThat(new SqlAccountRepository(database()).findByName(new PlayerName("Steve")))
                .isEmpty();
        WebsiteRows website =
                new WebsiteRows(database(), new WebsiteTable(true, "authme", WebsiteTable.Hash.SHA256, COLUMNS));
        assertThat(website.add(steve(hashing), hashing.hash(Password.of("racing1"))))
                .as("the website took the name between the look and the write")
                .isFalse();
        assertThat(sitePassword("steve")).contains(theirs);

        Account alex = new Account(
                UuidScheme.offline(new PlayerName("Alex")),
                new PlayerName("Alex"),
                AccountKind.CRACKED,
                null,
                hashing.hash(Password.of("alexpass1")),
                NOW,
                "a",
                null,
                null);
        accounts.insert(alex);
        madeOnTheWebsite("bob", "Bob", theirs);
        assertThat(accounts.rename(alex.id(), new PlayerName("Bob"), NOW))
                .isEqualTo(new AccountRepository.NameTaken(new PlayerName("Bob")));
        assertThat(sitePassword("alex")).isPresent();
        assertThat(sitePassword("bob")).contains(theirs);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a website row somebody made under the name of an account the plugin held first is not that"
            + " account's: its password opens nothing, and nothing the account does touches it")
    void aStrangersRowIsNotTrusted(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account steve = steve(hashing);
        new SqlAccountRepository(database()).insert(steve);
        String planted = hashing.hash(Password.of("planted1")).encoded();
        madeOnTheWebsite("steve", "Steve", planted);

        assertThat(accounts.findByName(new PlayerName("Steve")).orElseThrow().password())
                .isEqualTo(steve.password());
        assertThat(logIn(accounts, hashing, "Steve", "planted1")).isInstanceOf(LoginResult.Wrong.class);
        assertThat(logIn(accounts, hashing, "Steve", "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
        PasswordHash fresh = hashing.hash(Password.of("fresh777"));
        assertThat(accounts.replacePassword(steve.id(), steve.password(), fresh))
                .isTrue();
        accounts.update(steve.withPassword(fresh));
        Optional<String> address = database()
                .transaction(sql -> sql.queryFirst(
                        "SELECT ip FROM authme WHERE username = ?",
                        statement -> statement.setString(1, "steve"),
                        row -> String.valueOf(row.getString("ip"))));
        assertThat(address).as("the login is not written to their row").contains("null");
        accounts.rename(steve.id(), new PlayerName("Steven"), NOW);
        accounts.delete(steve.id());
        assertThat(sitePassword("steve")).contains(planted);
        assertThat(sitePassword("steven")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a website row holding the account's own hash, as an imported account's does, is the account's"
            + " from then on")
    void anImportedRowIsTrusted(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account steve = steve(hashing);
        new SqlAccountRepository(database()).insert(steve);
        madeOnTheWebsite(
                "steve", "Steve", Objects.requireNonNull(steve.password()).encoded());
        assertThat(accounts.findByName(new PlayerName("Steve"))).isPresent();

        String changed = hashing.hash(Password.of("fresh777")).encoded();
        database()
                .inTransaction(sql -> sql.update("UPDATE authme SET password = ? WHERE username = ?", statement -> {
                    statement.setString(1, changed);
                    statement.setString(2, "steve");
                }));
        assertThat(logIn(accounts, hashing, "Steve", "fresh777")).isInstanceOf(LoginResult.LoggedIn.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("the link to the website's row moves with the account's id, and goes with the account")
    void theLinkFollowsTheId(String engine) {
        open(engine);
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = accounts(WebsiteTable.Hash.SHA256);
        Account steve = steve(hashing);
        accounts.insert(steve);
        WebsiteRows website =
                new WebsiteRows(database(), new WebsiteTable(true, "authme", WebsiteTable.Hash.SHA256, COLUMNS));
        UUID turned = UUID.randomUUID();

        accounts.moveId(steve.id(), turned);
        assertThat(website.linked(turned)).isTrue();
        assertThat(website.linked(steve.id())).isFalse();
        new SqlAccountRepository(database()).delete(turned);
        assertThat(website.linked(turned)).isFalse();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("while the table is off the plugin's accounts are used alone, and no table is made")
    void offLeavesTheAccountsAlone() {
        open("sqlite");
        SqlAccountRepository own = new SqlAccountRepository(database());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThat(WebsiteAccounts.beside(
                        own, database(), new WebsiteTable(false, "authme", WebsiteTable.Hash.SHA256, COLUMNS), clock))
                .isSameAs(own);
        assertThatThrownBy(() -> sitePassword("steve")).as("no table was made").isInstanceOf(RuntimeException.class);
        assertThat(WebsiteAccounts.beside(
                        own, database(), new WebsiteTable(true, "authme", WebsiteTable.Hash.SHA256, COLUMNS), clock))
                .isInstanceOf(WebsiteAccounts.class);
        assertThat(sitePassword("steve")).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"sqlite", "h2"})
    @DisplayName("a table the website made is used as it is, other columns and all, under the operator's names")
    void anExistingTableIsUsed(String engine) {
        open(engine);
        String id = engine.equals("h2") ? "INTEGER AUTO_INCREMENT PRIMARY KEY" : "INTEGER PRIMARY KEY";
        database()
                .inTransaction(sql -> sql.execute("CREATE TABLE site_users (id " + id + ","
                        + " user_name VARCHAR(255) NOT NULL UNIQUE, real_name VARCHAR(255) NOT NULL,"
                        + " pass VARCHAR(255) NOT NULL, last_ip VARCHAR(40), seen BIGINT, joined BIGINT,"
                        + " joined_ip VARCHAR(40), credit INTEGER DEFAULT 7)"));
        Hashing hashing = hashing(WebsiteTable.Hash.SHA256);
        WebsiteAccounts accounts = WebsiteAccounts.over(
                new SqlAccountRepository(database()),
                database(),
                new WebsiteTable(
                        true,
                        "site_users",
                        WebsiteTable.Hash.SHA256,
                        new WebsiteTable.Columns(
                                "user_name", "real_name", "pass", "last_ip", "seen", "joined", "joined_ip")),
                Clock.fixed(NOW, ZoneOffset.UTC));

        accounts.insert(steve(hashing));

        Optional<String> kept = database()
                .transaction(sql -> sql.queryFirst(
                        "SELECT real_name, credit FROM site_users WHERE user_name = ?",
                        statement -> statement.setString(1, "steve"),
                        row -> row.getString("real_name") + " " + row.getInt("credit")));
        assertThat(kept).contains("Steve 7");
        assertThat(logIn(accounts, hashing, "Steve", "hunter22")).isInstanceOf(LoginResult.LoggedIn.class);
    }
}
