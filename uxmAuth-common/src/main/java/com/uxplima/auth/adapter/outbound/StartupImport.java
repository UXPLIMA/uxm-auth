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
import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.uxmlib.storage.sql.Database;

/**
 * Runs the import {@code config.conf} names, once, before the door opens.
 *
 * <p>The import runs inside a lock every server of the database shares, and the mark is read inside it, so two
 * servers that start together import once. A failure gives the lock back first and then stops the plugin: a
 * server that let players in on half the accounts would be worse than one that did not start.
 */
public final class StartupImport {

    /** Runs work while this server holds the import lock. */
    @FunctionalInterface
    public interface Lock {
        void holding(Runnable work);
    }

    private StartupImport() {}

    /**
     * Imports the named source unless it was imported before, and prints what it did, in counts.
     *
     * @param folder the imports folder, written from the jar before it is read
     * @param sources opens the source a layout describes
     * @return the report, or empty when no source is named
     */
    public static Optional<ImportReport> run(
            PluginSettings.Importing importing,
            Path folder,
            Function<ImportLayout, ImportSource> sources,
            ImportAccounts importer,
            Lock lock,
            Consumer<String> console) {
        Objects.requireNonNull(importing, "importing");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(lock, "lock");
        Objects.requireNonNull(console, "console");
        if (importing.source().isEmpty()) {
            return Optional.empty();
        }
        ImportLayout layout = layoutOf(importing, ImportLayout.shipped(Objects.requireNonNull(folder, "folder")));
        ImportReport report = locked(layout, sources, importer, lock);
        console.accept(lineOf(layout, report));
        return Optional.of(report);
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

    /** The layout the source names, on the table the operator named when they named one. */
    private static ImportLayout layoutOf(PluginSettings.Importing importing, Map<String, ImportLayout> layouts) {
        ImportLayout layout = layouts.get(importing.source());
        if (layout == null) {
            throw new IllegalArgumentException(
                    "import.source is " + importing.source() + ", and the imports folder has a layout for "
                            + String.join(", ", new TreeSet<>(layouts.keySet())));
        }
        if (layout.kind() == ImportLayout.Kind.TABLE
                && importing.address().jdbcUrl().isEmpty()) {
            throw new IllegalArgumentException("import.source is " + layout.key()
                    + ", and import.jdbc-url is empty: name the database it keeps its accounts in");
        }
        if (!importing.table().isEmpty()) {
            return layout.onTable(importing.table());
        }
        if (layout.table().isEmpty()) {
            throw new IllegalArgumentException("import.source " + layout.key()
                    + " keeps its accounts in a table it names itself: set import.table");
        }
        return layout;
    }

    /** Imports inside the lock, and throws a failure only once the lock is given back. */
    private static ImportReport locked(
            ImportLayout layout, Function<ImportLayout, ImportSource> sources, ImportAccounts importer, Lock lock) {
        AtomicReference<ImportReport> report = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        lock.holding(() -> {
            try (ImportSource source = sources.apply(layout)) {
                report.set(importer.run(source));
            } catch (RuntimeException failed) {
                failure.set(failed);
            }
        });
        RuntimeException failed = failure.get();
        if (failed != null) {
            throw new ImportFailed(
                    "the import from " + layout.display() + " failed, so the plugin stops: " + failed.getMessage()
                            + ". Nothing was marked complete, and the next start reads the source again from the"
                            + " beginning, so no account stays half-imported",
                    failed);
        }
        return Objects.requireNonNull(report.get(), "the lock ran no import");
    }

    private static String lineOf(ImportLayout layout, ImportReport report) {
        if (report.alreadyDone()) {
            return layout.display() + " was imported already, so the import did nothing; empty import.source to"
                    + " stop this line";
        }
        return "imported from " + layout.display() + ": " + report.imported() + " new, " + report.updated()
                + " updated, " + report.duplicates() + " duplicate names, " + report.refusedNameCount()
                + " names refused, " + report.refusedSecrets() + " authenticator secrets refused, hashes not read: "
                + (report.unknownHashes().isEmpty() ? "none" : report.unknownHashes());
    }
}
