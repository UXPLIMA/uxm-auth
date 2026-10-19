package com.uxplima.auth.adapter.outbound;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import com.uxplima.auth.application.migrate.ImportAccounts;
import com.uxplima.auth.application.migrate.ImportReport;
import com.uxplima.auth.application.migrate.MarkPremium;
import com.uxplima.auth.application.migrate.PremiumReport;
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.domain.name.PlayerName;
import com.uxplima.auth.domain.premium.PremiumAnswer;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * Runs the import {@code config.conf} names, once, before the door opens: the accounts first, then the premium list.
 *
 * <p>Both run inside a lock every server of the database shares, and each mark is read inside it, so two servers
 * that start together import once. A failure gives the lock back first and then stops the plugin: a server that
 * let players in on half the accounts would be worse than one that did not start.
 */
public final class StartupImport {

    /** Runs work while this server holds the import lock. */
    @FunctionalInterface
    public interface Lock {
        void holding(Runnable work);
    }

    /** What each half did, empty for a half nothing was named for. */
    public record Imported(Optional<ImportReport> accounts, Optional<PremiumReport> premium) {}

    /** A layout to read, and the address the operator named it with. */
    public record Named(ImportLayout layout, TableImport.Address address) {}

    private static final String ACCOUNTS = "import.";
    private static final String PREMIUM = "import.premium.";

    private StartupImport() {}

    /**
     * Imports what is named unless it was imported before, and prints what it did, in counts.
     *
     * @param folder the imports folder, written from the jar before it is read
     * @param sources opens the source a layout describes, at the address it was named with
     */
    public static Imported run(
            PluginSettings.Importing importing,
            Path folder,
            Function<Named, ImportSource> sources,
            ImportAccounts importer,
            MarkPremium marker,
            Lock lock,
            Consumer<String> console) {
        Objects.requireNonNull(importing, "importing");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(console, "console");
        if (importing.accounts().source().isEmpty()
                && importing.premium().source().isEmpty()) {
            return new Imported(Optional.empty(), Optional.empty());
        }
        Map<String, ImportLayout> layouts = ImportLayout.shipped(Objects.requireNonNull(folder, "folder"));
        Optional<Named> accounts = named(importing.accounts(), layouts, ACCOUNTS);
        Optional<Named> premium = named(importing.premium(), layouts, PREMIUM);
        AtomicReference<Imported> ran = new AtomicReference<>();
        guarded(Objects.requireNonNull(lock, "lock"), () -> {
            Optional<ImportReport> brought = accounts.map(one -> read(one, sources, importer::run));
            brought.ifPresent(
                    report -> console.accept(lineOf(accounts.orElseThrow().layout(), report)));
            Optional<PremiumReport> marked = premium.map(one -> read(one, sources, marker::run));
            marked.ifPresent(
                    report -> console.accept(lineOf(premium.orElseThrow().layout(), report)));
            ran.set(new Imported(brought, marked));
        });
        return Objects.requireNonNull(ran.get(), "the lock ran no import");
    }

    /**
     * The importer a start runs. It writes to the plugin's own tables and never to a website's, which may be the
     * very table the import reads.
     */
    public static ImportAccounts importer(Database database, Factors factors, PluginSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new ImportAccounts(
                new SqlAccountRepository(database),
                factors,
                new SqlImportMarks(database),
                () -> settings.current().namePolicy(),
                settings.current().ownership().uuidScheme(),
                Clock.systemUTC());
    }

    /** The premium list reader a start runs, over the plugin's own tables. */
    public static MarkPremium marker(
            Database database, Function<PlayerName, PremiumAnswer> lookup, PluginSettings settings) {
        Objects.requireNonNull(settings, "settings");
        return new MarkPremium(
                new SqlAccountRepository(database),
                new SqlImportMarks(database),
                () -> settings.current().namePolicy(),
                lookup,
                settings.current().ownership().uuidScheme(),
                Clock.systemUTC());
    }

    /** The layout a half names, on the table the operator named when they named one, refused by its key. */
    private static Optional<Named> named(
            PluginSettings.Importing.From from, Map<String, ImportLayout> layouts, String at) {
        if (from.source().isEmpty()) {
            return Optional.empty();
        }
        ImportLayout layout = layouts.get(from.source());
        boolean premiumHalf = at.equals(PREMIUM);
        if (layout == null || (layout.kind() == ImportLayout.Kind.PREMIUM) != premiumHalf) {
            throw new IllegalArgumentException(at + "source is " + from.source() + ", and the imports folder has a "
                    + (premiumHalf ? "premium list" : "layout") + " for " + keys(layouts, premiumHalf)
                    + (premiumHalf ? "" : "; a premium list goes under " + PREMIUM + "source"));
        }
        if (layout.kind() != ImportLayout.Kind.YAML && from.address().jdbcUrl().isEmpty()) {
            throw new IllegalArgumentException(at + "source is " + layout.key() + ", and " + at
                    + "jdbc-url is empty: name the database it keeps its accounts in");
        }
        if (!from.table().isEmpty()) {
            return Optional.of(new Named(layout.onTable(from.table()), from.address()));
        }
        if (layout.table().isEmpty()) {
            throw new IllegalArgumentException(at + "source " + layout.key()
                    + " keeps its accounts in a table it names itself: set " + at + "table");
        }
        return Optional.of(new Named(layout, from.address()));
    }

    private static String keys(Map<String, ImportLayout> layouts, boolean premium) {
        TreeSet<String> keys = new TreeSet<>();
        layouts.forEach((key, layout) -> {
            if ((layout.kind() == ImportLayout.Kind.PREMIUM) == premium) {
                keys.add(key);
            }
        });
        return String.join(", ", keys);
    }

    /** Reads one source, and names it in the failure that stops the plugin. */
    private static <R> R read(Named named, Function<Named, ImportSource> sources, Function<ImportSource, R> run) {
        try (ImportSource source = sources.apply(named)) {
            return run.apply(source);
        } catch (RuntimeException failed) {
            throw new ImportFailed(
                    "the import from " + named.layout().display() + " failed, so the plugin stops: "
                            + failed.getMessage()
                            + ". Nothing was marked complete, and the next start reads the source again from the"
                            + " beginning, so no account stays half-imported",
                    failed);
        }
    }

    /** Runs the work inside the lock, and throws its failure only once the lock is given back. */
    private static void guarded(Lock lock, Runnable work) {
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        lock.holding(() -> {
            try {
                work.run();
            } catch (RuntimeException failed) {
                failure.set(failed);
            }
        });
        RuntimeException failed = failure.get();
        if (failed != null) {
            throw failed;
        }
    }

    private static String lineOf(ImportLayout layout, ImportReport report) {
        if (report.alreadyDone()) {
            return done(layout);
        }
        return "imported from " + layout.display() + ": " + report.imported() + " new, " + report.updated()
                + " updated, " + report.duplicates() + " duplicate names, " + report.refusedNameCount()
                + " names refused, " + report.refusedSecrets() + " authenticator secrets refused, hashes not read: "
                + (report.unknownHashes().isEmpty() ? "none" : report.unknownHashes());
    }

    private static String lineOf(ImportLayout layout, PremiumReport report) {
        if (report.alreadyDone()) {
            return done(layout);
        }
        return "read the premium list of " + layout.display() + ": " + report.marked() + " turned premium, "
                + report.already() + " not cracked, " + report.unknownNames() + " names with no account, "
                + report.taken() + " ids another account holds, " + report.notOwner() + " old names, "
                + report.unanswered() + " names no source could place, " + report.refusedNames() + " names refused";
    }

    private static String done(ImportLayout layout) {
        return layout.display() + " was imported already, so the import did nothing; empty its source to stop this"
                + " line";
    }
}
