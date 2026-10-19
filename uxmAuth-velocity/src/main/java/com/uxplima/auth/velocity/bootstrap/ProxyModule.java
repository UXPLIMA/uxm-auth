package com.uxplima.auth.velocity.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.CommandFile;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.SchemeChanged;
import com.uxplima.auth.adapter.outbound.mail.MailFiles;
import com.uxplima.auth.application.message.Keys;
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
    private static final String COMMANDS = "commands.conf";
    private static final String COMMON_PASSWORDS = "passwords/common.txt";
    private static final String MESSAGES = "messages";
    private static final String THEME_FOLDER = "uxmTheme";
    private static final String POOL = "uxmAuth";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path folder;
    private @Nullable Database database;
    private @Nullable Messages messages;
    private @Nullable ProxyGraph graph;

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
        PluginSettings.Importing importing = settings.current().importing();
        if (!importing.accounts().source().isEmpty()
                || !importing.premium().source().isEmpty()) {
            // A server behind the proxy imports: it shares this database, and it knows the names a source
            // without names needs.
            logger.warn("An import source is set, and the proxy imports nothing. Set it in config.conf on a server"
                    + " behind the proxy instead.");
        }
        settings.current().protection().problems().forEach(logger::warn);
        Messages words = new Messages(
                catalog(settings), new ProxyLocales(() -> settings.current().language()));
        this.messages = words;
        ProxyGraph network = new ProxyGraph(plugin, proxy, logger, folder);
        this.graph = network;
        network.open(settings, opened, words);
        proxy.getEventManager().register(plugin, ProxyShutdownEvent.class, this::onShutdown);
        return true;
    }

    void onShutdown(ProxyShutdownEvent event) {
        Objects.requireNonNull(event, "event");
        shutdown();
    }

    /** Give back what the proxy does not take back by itself. */
    void shutdown() {
        ProxyGraph network = graph;
        if (network != null) {
            network.close();
            graph = null;
        }
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
        Languages languages =
                languagesIn(folder.resolve(MESSAGES), fallback, getClass().getClassLoader());
        for (String problem : languages.problems()) {
            logger.warn(problem);
        }
        return new Styler(theme()).style(languages.catalog(), List.of(Keys.values()), languages.entries(), fallback);
    }

    /**
     * The language files in this folder, over the ones this release carries. A file an older release wrote lacks
     * every line added since, and read alone each of those reached its readers in the default language.
     */
    static Languages languagesIn(Path messages, Locale defaultLocale, ClassLoader resources) {
        return Languages.load(messages, defaultLocale, resources, MESSAGES);
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
        // The waiting world reads the words for /login and /register from the file a server reads them from.
        copyIfAbsent(COMMANDS, folder.resolve(COMMANDS));
        copyIfAbsent(COMMON_PASSWORDS, folder.resolve(COMMON_PASSWORDS));
        try {
            if (PluginSettings.bringUpToDate(folder.resolve(CONFIG), getClass().getClassLoader())) {
                logger.info("config.conf has the keys this release adds. Nothing you wrote changed, and the file as it"
                        + " was is beside it as config.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            logger.warn("config.conf could not be brought up to date: {}", unreadable.getMessage());
        }
        mergeShippedCommands();
        copyIfAbsent("theme.conf", ThemeFiles.shared(folder, THEME_FOLDER));
        LanguageFiles.extractShipped(getClass().getClassLoader(), MESSAGES, folder.resolve(MESSAGES));
        MailFiles.extractShipped(getClass().getClassLoader(), folder.resolve(MailFiles.FOLDER));
    }

    /** The commands a release adds reach an older commands.conf, so the operator can rename them too. */
    private void mergeShippedCommands() {
        try {
            if (CommandFile.bringUpToDate(folder.resolve(COMMANDS), getClass().getClassLoader())) {
                logger.info("commands.conf has the commands this release adds. Nothing you wrote changed, and the"
                        + " file as it was is beside it as commands.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            logger.warn("commands.conf could not be brought up to date: {}", unreadable.getMessage());
        }
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
