package com.uxplima.auth.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.auth.application.migrate.ImportAccounts;
import com.uxplima.auth.application.migrate.ImportReport;
import com.uxplima.auth.application.migrate.MarkPremium;
import com.uxplima.auth.application.port.ImportMarks;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.account.AccountKind;
import com.uxplima.auth.domain.hash.LegacyHashes;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.testing.InMemoryAccounts;
import com.uxplima.auth.testing.InMemoryFactors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The import config.conf names runs at the start, once, inside the lock every member of a network shares. */
final class StartupImportTest {

    private static final UUID PAID = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    private static final String PLAIN_256 = "20d2fe5e369db54ec7090639a9dc30ec4d608604936239d39e2de07fda09eb0b";

    @TempDir
    Path folder;

    private final Map<String, Instant> marks = new ConcurrentHashMap<>();
    private final List<String> console = new CopyOnWriteArrayList<>();
    private final List<String> locked = new CopyOnWriteArrayList<>();
    private final List<ImportLayout> opened = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    private final ImportMarks marked = new ImportMarks() {
        @Override
        public Optional<Instant> completed(String key) {
            return Optional.ofNullable(marks.get(key));
        }

        @Override
        public void complete(String key, Instant at) {
            marks.put(key, at);
        }
    };

    private final InMemoryAccounts accounts = new InMemoryAccounts();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-19T08:00:00Z"), ZoneOffset.UTC);
    private final NamePolicy policy = new NamePolicy(Pattern.compile("[A-Za-z0-9_]+"), 3, 16, List.of());
    private final Supplier<NamePolicy> names = this::policy;
    private final ImportAccounts importer =
            new ImportAccounts(accounts, new InMemoryFactors(), marked, names, UuidScheme.OFFLINE, clock);
    private final MarkPremium marker =
            new MarkPremium(accounts, marked, names, name -> new PremiumAnswer.Unknown(), UuidScheme.OFFLINE, clock);

    private NamePolicy policy() {
        return policy;
    }

    private ImportSource source(ImportLayout layout) {
        opened.add(layout);
        return new ImportSource() {
            @Override
            public String key() {
                return layout.key();
            }

            @Override
            public Stream<ImportedAccount> read() {
                if (failing) {
                    throw new IllegalStateException("the source database refused the connection");
                }
                if (layout.kind() == ImportLayout.Kind.PREMIUM) {
                    return Stream.of(new ImportedAccount("Steve", PAID, PAID, null, null, null, null, null));
                }
                return Stream.of(new ImportedAccount(
                        "Steve",
                        null,
                        null,
                        LegacyHashes.rewrite(LegacyHashes.Style.PLAIN, PLAIN_256, null, null),
                        null,
                        null,
                        null,
                        null));
            }

            @Override
            public void close() {}
        };
    }

    private Optional<ImportReport> start(String source, String table) {
        return start(source, table, "jdbc:sqlite:x.db");
    }

    private Optional<ImportReport> start(String source, String table, String jdbcUrl) {
        return start(from(source, table, jdbcUrl), from("", "", "")).accounts();
    }

    private static PluginSettings.Importing.From from(String source, String table, String jdbcUrl) {
        return new PluginSettings.Importing.From(source, new TableImport.Address(jdbcUrl, "", ""), table);
    }

    private StartupImport.Imported start(
            PluginSettings.Importing.From accounts, PluginSettings.Importing.From premium) {
        return StartupImport.run(
                new PluginSettings.Importing(accounts, premium),
                folder.resolve("imports"),
                named -> source(named.layout()),
                importer,
                marker,
                work -> {
                    locked.add("taken");
                    work.run();
                    locked.add("given back");
                },
                console::add);
    }

    @Test
    @DisplayName("no source named: nothing is read, nothing is locked, nothing is said")
    void nothingNamedDoesNothing() {
        assertThat(start("", "")).isEmpty();

        assertThat(opened).isEmpty();
        assertThat(locked).isEmpty();
        assertThat(console).isEmpty();
    }

    @Test
    @DisplayName("a named source is imported inside the lock, its counts printed, and a second start only says so")
    void aSourceIsImportedOnce() {
        Optional<ImportReport> first = start("authme", "");

        assertThat(first)
                .hasValueSatisfying(report -> assertThat(report.imported()).isEqualTo(1));
        assertThat(locked).containsExactly("taken", "given back");
        assertThat(console).singleElement().asString().contains("AuthMe").contains("1 new");
        assertThat(marks).containsKey("authme");

        console.clear();
        Optional<ImportReport> second = start("authme", "");
        assertThat(second)
                .hasValueSatisfying(report -> assertThat(report.alreadyDone()).isTrue());
        assertThat(console).singleElement().asString().contains("already");
    }

    @Test
    @DisplayName("a renamed table is read in place of the layout's, and a source with no table of its own needs one")
    void theTableCanBeNamed() {
        start("authme", "site_users");
        assertThat(opened).singleElement().extracting(ImportLayout::table).isEqualTo("site_users");

        assertThatThrownBy(() -> start("jpremium", "")).hasMessageContaining("import.table");
    }

    @Test
    @DisplayName("a table source with no database named is refused before anything is locked or opened")
    void aTableNeedsADatabase() {
        assertThatThrownBy(() -> start("authme", "", ""))
                .isNotInstanceOf(ImportFailed.class)
                .hasMessageContaining("import.jdbc-url");
        assertThat(locked).isEmpty();
        assertThat(opened).isEmpty();
    }

    @Test
    @DisplayName("a source in a file of its own needs no database")
    void aFileSourceNeedsNoDatabase() {
        start("authplus", "", "");

        assertThat(opened).singleElement().extracting(ImportLayout::kind).isEqualTo(ImportLayout.Kind.YAML);
    }

    @Test
    @DisplayName("a source the folder has no layout for is refused with the ones it has")
    void anUnknownSourceIsRefused() {
        assertThatThrownBy(() -> start("someplugin", ""))
                .hasMessageContaining("import.source")
                .hasMessageContaining("authme");
    }

    @Test
    @DisplayName("a failed import names the source and says why the plugin stops, and marks nothing")
    void aFailureStopsThePlugin() {
        failing = true;

        assertThatThrownBy(() -> start("authme", ""))
                .isInstanceOf(ImportFailed.class)
                .hasMessageContaining("AuthMe")
                .hasMessageContaining("refused the connection")
                .hasMessageContaining("half-imported");
        assertThat(marks).isEmpty();
        assertThat(locked).containsExactly("taken", "given back");
    }

    @Test
    @DisplayName("a premium list is read after the accounts, inside the same lock, and turns what they brought premium")
    void thePremiumListComesLast() {
        StartupImport.Imported ran =
                start(from("authme", "", "jdbc:sqlite:a.db"), from("fastlogin", "", "jdbc:sqlite:f.db"));

        assertThat(opened).extracting(ImportLayout::key).containsExactly("authme", "fastlogin");
        assertThat(locked).containsExactly("taken", "given back");
        assertThat(ran.premium())
                .hasValueSatisfying(report -> assertThat(report.marked()).isEqualTo(1));
        assertThat(accounts.findByName(new PlayerName("Steve")).orElseThrow().kind())
                .isEqualTo(AccountKind.PREMIUM);
        assertThat(console).hasSize(2);
        assertThat(console.get(1)).contains("FastLogin").contains("1 turned premium");
    }

    @Test
    @DisplayName("an account import that fails reads no premium list")
    void aFailureReadsNoPremiumList() {
        failing = true;

        assertThatThrownBy(
                        () -> start(from("authme", "", "jdbc:sqlite:a.db"), from("fastlogin", "", "jdbc:sqlite:f.db")))
                .isInstanceOf(ImportFailed.class);
        assertThat(opened).extracting(ImportLayout::key).containsExactly("authme");
        assertThat(marks).isEmpty();
    }

    @Test
    @DisplayName("a premium list alone reads the accounts there are, and each half refuses the other's layouts")
    void eachHalfTakesItsOwnLayouts() {
        StartupImport.Imported ran = start(from("", "", ""), from("fastlogin", "", "jdbc:sqlite:f.db"));
        assertThat(ran.accounts()).isEmpty();
        assertThat(ran.premium())
                .hasValueSatisfying(report -> assertThat(report.unknownNames()).isEqualTo(1));

        assertThatThrownBy(() -> start(from("fastlogin", "", "jdbc:sqlite:f.db"), from("", "", "")))
                .hasMessageContaining("import.premium.source");
        assertThatThrownBy(() -> start(from("", "", ""), from("authme", "", "jdbc:sqlite:a.db")))
                .hasMessageContaining("import.premium.source")
                .hasMessageContaining("fastlogin");
    }
}
