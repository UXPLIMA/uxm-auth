package com.uxplima.auth.adapter.outbound;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import com.uxplima.auth.domain.hash.Argon2idHasher;
import com.uxplima.auth.domain.hash.AuthMeSha256Hasher;
import com.uxplima.auth.domain.hash.BcryptHasher;
import com.uxplima.auth.domain.hash.HashAlgorithm;
import com.uxplima.auth.domain.hash.Hashing;
import com.uxplima.auth.domain.hash.PasswordHasher;
import com.uxplima.auth.domain.hash.Pbkdf2Hasher;
import com.uxplima.auth.domain.name.NamePolicy;
import com.uxplima.auth.domain.password.CommonPasswords;
import com.uxplima.auth.domain.password.PasswordPolicy;
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
    public record Snapshot(
            LanguageSettings language,
            Storage storage,
            AuthSettings.Names names,
            AuthSettings.Passwords passwords,
            AuthSettings.HashSettings hashing,
            AuthSettings.Lane lane,
            AuthSettings.Hold hold,
            AuthSettings.Timeouts timeouts,
            AuthSettings.Effects effects,
            AuthSettings.Bedrock bedrock,
            AuthSettings.Premium premium,
            AuthSettings.Ownership ownership,
            AuthSettings.Network network,
            ProtectionSettings protection,
            AuthSettings.PasswordLists passwordLists,
            CommonPasswords common) {

        public Snapshot {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(names, "names");
            Objects.requireNonNull(passwords, "passwords");
            Objects.requireNonNull(hashing, "hashing");
            Objects.requireNonNull(lane, "lane");
            Objects.requireNonNull(hold, "hold");
            Objects.requireNonNull(timeouts, "timeouts");
            Objects.requireNonNull(protection, "protection");
            Objects.requireNonNull(effects, "effects");
            Objects.requireNonNull(bedrock, "bedrock");
            Objects.requireNonNull(premium, "premium");
            Objects.requireNonNull(ownership, "ownership");
            Objects.requireNonNull(network, "network");
            Objects.requireNonNull(passwordLists, "passwordLists");
            Objects.requireNonNull(common, "common");
            if (hashing.algorithm() == HashAlgorithm.BCRYPT && passwords.maxLength() > AuthSettings.BCRYPT_BYTES) {
                throw new IllegalArgumentException("passwords.max-length is " + passwords.maxLength()
                        + ", and bcrypt reads only the first " + AuthSettings.BCRYPT_BYTES
                        + " bytes: lower it, or choose another hashing.algorithm");
            }
            // Built once here, so a value the domain refuses is refused when the file is read.
            namePolicyOf(names);
            passwordPolicyOf(passwords, hashing, common);
            hashersOf(hashing);
        }

        /** The language every message falls back to. */
        public Locale defaultLocale() {
            return language.defaultLocale();
        }

        public NamePolicy namePolicy() {
            return namePolicyOf(names);
        }

        /** The password rules. Under bcrypt they also refuse a password past its 72 bytes. */
        public PasswordPolicy passwordPolicy() {
            return passwordPolicyOf(passwords, hashing, common);
        }

        /** The current algorithm for new hashes, and all four for reading old ones. */
        public Hashing hashers() {
            return hashersOf(hashing);
        }

        private static NamePolicy namePolicyOf(AuthSettings.Names names) {
            return new NamePolicy(
                    Pattern.compile(names.pattern()), names.minLength(), names.maxLength(), names.blocked());
        }

        private static PasswordPolicy passwordPolicyOf(
                AuthSettings.Passwords passwords, AuthSettings.HashSettings hashing, CommonPasswords common) {
            int maxBytes = hashing.algorithm() == HashAlgorithm.BCRYPT ? AuthSettings.BCRYPT_BYTES : 0;
            return new PasswordPolicy(
                    passwords.minLength(),
                    passwords.maxLength(),
                    Pattern.compile(passwords.allowed()),
                    passwords.required(),
                    maxBytes,
                    common);
        }

        private static Hashing hashersOf(AuthSettings.HashSettings hashing) {
            List<PasswordHasher> all = List.of(
                    new Argon2idHasher(
                            hashing.argon2MemoryKib(), hashing.argon2Iterations(), hashing.argon2Parallelism()),
                    new BcryptHasher(hashing.bcryptCost()),
                    new Pbkdf2Hasher(hashing.pbkdf2Iterations()),
                    new AuthMeSha256Hasher());
            PasswordHasher current = all.stream()
                    .filter(hasher -> hasher.algorithm() == hashing.algorithm())
                    .findFirst()
                    .orElseThrow();
            return new Hashing(current, all);
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
        AuthSettings.PasswordLists lists = SettingsReader.passwordLists(config);
        return new Snapshot(
                language(config),
                storage(config),
                SettingsReader.names(config),
                SettingsReader.passwords(config),
                SettingsReader.hashing(config),
                SettingsReader.lane(config),
                SettingsReader.hold(config),
                SettingsReader.timeouts(config),
                SettingsReader.effects(config),
                SettingsReader.bedrock(config),
                SettingsReader.premium(config),
                SettingsReader.ownership(config),
                SettingsReader.network(config),
                ProtectionReader.protection(config),
                lists,
                common(lists));
    }

    /** The common passwords, read from beside this file at every load; a file that is not there lists none. */
    private CommonPasswords common(AuthSettings.PasswordLists lists) {
        Path folder = file.toAbsolutePath().getParent();
        Path list = folder == null ? Path.of(lists.commonFile()) : folder.resolve(lists.commonFile());
        if (!Files.isRegularFile(list)) {
            return CommonPasswords.none();
        }
        try {
            return CommonPasswords.of(Files.readAllLines(list, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            throw new IllegalArgumentException("passwords.common-file " + list + " cannot be read", unreadable);
        }
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
