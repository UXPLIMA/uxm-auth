package com.uxplima.auth.velocity.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.DoorWords;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.SchemeChanged;
import com.uxplima.auth.adapter.outbound.SqlAccountRepository;
import com.uxplima.auth.adapter.outbound.SqlDisputes;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.application.ReadTheName;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.premium.AtTheDoor;
import com.uxplima.auth.application.premium.LetPremiumIn;
import com.uxplima.auth.application.premium.Moves;
import com.uxplima.auth.application.premium.NameMemory;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.domain.premium.UuidScheme;
import com.uxplima.auth.velocity.adapter.inbound.DoorListener;
import com.uxplima.auth.velocity.adapter.inbound.ProxyDoor;
import com.uxplima.auth.velocity.adapter.outbound.ProxyLocales;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.language.LanguageFiles;
import com.uxplima.uxmlib.text.language.Languages;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import com.uxplima.uxmlib.text.style.ThemeFiles;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The composition root of the proxy half. Every object is built here and handed its collaborators through a
 * constructor; Guice reaches only the plugin shell.
 *
 * <p>It reads the same config.conf a server reads, opens the same database the servers share, and holds the
 * UUID scheme against the one the database recorded, exactly as a server does.
 */
final class ProxyModule {

    private static final String CONFIG = "config.conf";
    private static final String MESSAGES = "messages";
    private static final String THEME_FOLDER = "uxmTheme";
    private static final String POOL = "uxmAuth";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private final HttpClient web = HttpClient.newHttpClient();
    private @Nullable Database database;
    private @Nullable Messages messages;

    ProxyModule(Object plugin, ProxyServer proxy, Logger logger, Path folder) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    /**
     * Build the graph and register what the proxy must see.
     *
     * @return whether the proxy half started; a database recorded under another UUID scheme stops it, with the
     *     reason in the console, because every player would come back to an empty inventory
     */
    boolean wire() {
        extractDefaults();
        PluginSettings settings = new PluginSettings(folder.resolve(CONFIG));
        Database opened = AuthDatabase.open(settings.current().storage(), POOL);
        try {
            new RecordedScheme(opened).check(settings.current().ownership().uuidScheme());
        } catch (SchemeChanged changed) {
            opened.close();
            logger.error(changed.getMessage());
            return false;
        }
        this.database = opened;
        warnOfAFileDatabase(settings.current().storage());
        Messages words = new Messages(
                catalog(settings), new ProxyLocales(() -> settings.current().language()));
        this.messages = words;
        openTheDoor(settings, opened, words);
        proxy.getEventManager().register(plugin, ProxyShutdownEvent.class, this::onShutdown);
        return true;
    }

    /**
     * Put the identity decision before every connection, unless the proxy is in online mode: then Velocity asks
     * Mojang for every player itself, and a cracked player could never join.
     */
    private void openTheDoor(PluginSettings settings, Database database, Messages words) {
        if (proxy.getConfiguration().isOnlineMode()) {
            logger.warn("online-mode is on in velocity.toml, so the proxy proves every player itself. Premium"
                    + " auto-login stays off; turn online-mode off to let uxmAuth ask paid accounts alone.");
            return;
        }
        Clock clock = Clock.systemUTC();
        SqlAccountRepository accounts = new SqlAccountRepository(database);
        SqlDisputes disputes = new SqlDisputes(database);
        UuidScheme scheme = settings.current().ownership().uuidScheme();
        AtTheDoor atTheDoor = atTheDoor(settings, accounts, disputes, clock);
        ProxyDoor door = new ProxyDoor(
                atTheDoor,
                new LetPremiumIn(
                        accounts,
                        disputes,
                        () -> settings.current().ownership().conflict(),
                        () -> settings.current().ownership().renameMark(),
                        scheme,
                        clock),
                new Moves(accounts),
                new ReadTheName(
                        accounts,
                        () -> settings.current().namePolicy(),
                        () -> settings.current().passwords().maxAttempts()),
                () -> settings.current().ownership().crackedMark(),
                scheme,
                clock);
        proxy.getEventManager().register(plugin, new DoorListener(door, new DoorWords(words), logger));
        logger.info("Premium auto-login is on: paid accounts are proved by Mojang at the proxy.");
    }

    /** The decision at the pre-login: the web sources, the operator's rule, and the memories of claims and failures. */
    private AtTheDoor atTheDoor(
            PluginSettings settings, SqlAccountRepository accounts, SqlDisputes disputes, Clock clock) {
        return new AtTheDoor(
                accounts,
                () -> settings.current().namePolicy(),
                new PremiumLookup(
                        new PremiumSources(web, () -> settings.current().premium(), clock)::ordered,
                        () -> settings.current().premium().keepAnswers(),
                        clock),
                () -> settings.current().ownership().decision(),
                new NameMemory(() -> settings.current().ownership().failedMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                new NameMemory(() -> settings.current().ownership().claimMemory(), clock),
                disputes);
    }

    void onShutdown(ProxyShutdownEvent event) {
        Objects.requireNonNull(event, "event");
        shutdown();
    }

    /** Give back what the proxy does not take back by itself. */
    void shutdown() {
        Database open = database;
        if (open != null) {
            open.close();
            database = null;
        }
    }

    /** The database the proxy shares with the servers, once the proxy half has started. */
    @Nullable Database database() {
        return database;
    }

    /** The catalogue every message on the proxy is read from, once the proxy half has started. */
    @Nullable Messages messages() {
        return messages;
    }

    /**
     * The proxy and the servers must read one database, and a file beside the proxy is not shared with servers on
     * other machines. It still starts, because a network on one machine can point every member at one file.
     */
    private void warnOfAFileDatabase(PluginSettings.Storage storage) {
        String url = storage.jdbcUrl().toLowerCase(Locale.ROOT);
        if (url.startsWith("jdbc:sqlite:") || url.startsWith("jdbc:h2:")) {
            logger.warn("storage.jdbc-url is a file on this machine. The proxy and every server behind it must read"
                    + " one database: point them all at one MySQL, MariaDB or PostgreSQL.");
        }
    }

    /** The words, painted, read from the folder so a language is gained by gaining a file. */
    private MessageCatalog catalog(PluginSettings settings) {
        Locale fallback = settings.current().defaultLocale();
        Languages languages = Languages.load(folder.resolve(MESSAGES), fallback);
        for (String problem : languages.problems()) {
            logger.warn(problem);
        }
        return new Styler(theme()).style(languages.catalog(), List.of(Keys.values()), languages.entries(), fallback);
    }

    private Theme theme() {
        try {
            return ThemeFiles.load(ThemeFiles.shared(folder, THEME_FOLDER), ThemeFiles.own(folder));
        } catch (ConfigurateException | IllegalArgumentException unreadable) {
            logger.warn("Cannot read theme.conf. The shipped palette is used.", unreadable);
            return Theme.defaults();
        }
    }

    /** Write the shipped files on the first start, and give an older config.conf the keys this release adds. */
    private void extractDefaults() {
        copyIfAbsent(CONFIG, folder.resolve(CONFIG));
        try {
            if (PluginSettings.bringUpToDate(folder.resolve(CONFIG), getClass().getClassLoader())) {
                logger.info("config.conf has the keys this release adds. Nothing you wrote changed, and the file as it"
                        + " was is beside it as config.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            logger.warn("config.conf could not be brought up to date: {}", unreadable.getMessage());
        }
        copyIfAbsent("theme.conf", ThemeFiles.shared(folder, THEME_FOLDER));
        LanguageFiles.extractShipped(getClass().getClassLoader(), MESSAGES, folder.resolve(MESSAGES));
    }

    private void copyIfAbsent(String resource, Path target) {
        if (Files.isRegularFile(target)) {
            return;
        }
        try (InputStream shipped = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (shipped == null) {
                return;
            }
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.copy(shipped, target);
        } catch (IOException unwritable) {
            logger.warn("Cannot write {}. The shipped one is used.", target, unwritable);
        }
    }
}
