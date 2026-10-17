package com.uxplima.auth.adapter.outbound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.uxmlib.config.HoconConfig;
import com.uxplima.uxmlib.text.language.LanguageSettings;
import org.jspecify.annotations.Nullable;

/**
 * The typed view of {@code config.conf}. The file is read once on enable and once per reload, and the
 * result is swapped in as one immutable snapshot. A caller reads {@link #current()} on the hot path, so no
 * caller ever sees a half-applied reload.
 *
 * <p>Nothing in this plugin reads a configuration value by string path outside this class.
 */
public final class PluginSettings {

    /**
     * Add the keys this release ships to a file an operator already has.
     *
     * <p>It lives here because this is the one class that reads the configuration file, and the guard that
     * says so is the reason: a second class touching {@code HoconConfig} is a second place the file surface
     * can drift. Nothing a person wrote is changed, and the file as it was is kept beside it.
     *
     * @return whether anything was added, so the caller can say so once in the console
     */
    public static boolean bringUpToDate(Path file, ClassLoader resources) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(resources, "resources");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        return HoconConfig.load(file).mergeDefaults("config.conf", resources);
    }

    /** One immutable snapshot of the file. */
    public record Snapshot(LanguageSettings language, Storage storage) {

        public Snapshot {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(storage, "storage");
        }

        /** The language every message falls back to. */
        public Locale defaultLocale() {
            return language.defaultLocale();
        }
    }

    /** Where the accounts live. */
    public record Storage(String jdbcUrl, String username, String password, int poolSize) {

        public Storage {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
            if (poolSize < 1) {
                throw new IllegalArgumentException("storage.pool-size must be at least 1, got: " + poolSize);
            }
        }

        /** Everything but the password, because a settings snapshot is the kind of thing that gets logged. */
        @Override
        public String toString() {
            return "Storage[jdbcUrl=" + jdbcUrl + ", username=" + username + ", password=hidden, poolSize=" + poolSize
                    + "]";
        }
    }

    /** The file the accounts are kept in when the operator names no database. */
    private static final String SQLITE_FILE = "auth.db";

    private final Path file;

    // A volatile reference, not an AtomicReference. A reference assignment is already atomic, and volatile
    // gives the visibility that a reader on another region thread needs. AtomicReference would only add a
    // compare-and-set that nothing here uses, and its get() is nullable, which this field never is.
    private volatile Snapshot current;

    public PluginSettings(Path file) {
        this.file = Objects.requireNonNull(file, "file");
        this.current = read();
    }

    public Snapshot current() {
        return current;
    }

    /** What runs at the end of every reload, in the order it was registered. */
    private final List<Runnable> afterReload = new ArrayList<>();

    /**
     * Run {@code action} after every reload, once the new snapshot is in place.
     *
     * <p>The words and the palette are read by the module rather than here, and they have to move at the same
     * moment as the numbers: a reload that repaints half of what a player sees is worse than one that repaints
     * none. See UI-STYLE.md section 14.2.
     */
    public void afterReload(Runnable action) {
        afterReload.add(Objects.requireNonNull(action, "action"));
    }

    /**
     * Re-read the file. A parse error propagates, and the previous snapshot stays in place, so the server
     * keeps running on the last configuration that was known to be good.
     */
    public void reload() {
        this.current = read();
        for (Runnable action : afterReload) {
            action.run();
        }
    }

    private Snapshot read() {
        HoconConfig config = HoconConfig.load(file);
        return new Snapshot(language(config), storage(config));
    }

    /**
     * How a player's language is answered.
     *
     * <p>The three keys are the same in every plugin of ours, and uxmLib holds the order they are read in.
     * See {@code docs/04-i18n.md}.
     */
    private static LanguageSettings language(HoconConfig config) {
        return new LanguageSettings(
                localeOf(config.getString("language.default", "en"), Locale.ENGLISH),
                config.getBoolean("language.follow-client", true),
                forcedLocale(config.getString("language.force", "")));
    }

    /**
     * An empty URL is the SQLite file beside this configuration, so a server that sets nothing has accounts
     * that survive a restart without anybody installing a database.
     */
    private Storage storage(HoconConfig config) {
        String configured = config.getString("storage.jdbc-url", "").trim();
        Path folder = Objects.requireNonNull(file.toAbsolutePath().getParent(), "the configuration has no folder");
        return new Storage(
                configured.isEmpty() ? "jdbc:sqlite:" + folder.resolve(SQLITE_FILE) : configured,
                config.getString("storage.username", ""),
                config.getString("storage.password", ""),
                config.getInt("storage.pool-size", 4));
    }

    private static @Nullable Locale forcedLocale(String tag) {
        return tag.isBlank() ? null : localeOf(tag, Locale.ENGLISH);
    }

    private static Locale localeOf(String tag, Locale fallback) {
        Locale locale = Locale.forLanguageTag(tag.trim().replace('_', '-'));
        return locale.getLanguage().isEmpty() ? fallback : locale;
    }
}
