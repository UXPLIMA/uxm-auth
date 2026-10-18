package com.uxplima.auth.paper.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.PluginHealth;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.RecordedScheme;
import com.uxplima.auth.adapter.outbound.ScreenFile;
import com.uxplima.auth.adapter.outbound.mail.MailFiles;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.paper.adapter.inbound.command.AdminCommand;
import com.uxplima.auth.paper.adapter.inbound.command.CaptchaCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ChangePasswordCommand;
import com.uxplima.auth.paper.adapter.inbound.command.ClaimCommand;
import com.uxplima.auth.paper.adapter.inbound.command.EmailCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LanguageSuggestions;
import com.uxplima.auth.paper.adapter.inbound.command.LoginCommand;
import com.uxplima.auth.paper.adapter.inbound.command.LogoutCommand;
import com.uxplima.auth.paper.adapter.inbound.command.PremiumCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RecoverCommand;
import com.uxplima.auth.paper.adapter.inbound.command.RegisterCommand;
import com.uxplima.auth.paper.adapter.inbound.command.SessionCommand;
import com.uxplima.auth.paper.adapter.inbound.command.TwoFactorCommand;
import com.uxplima.auth.paper.adapter.inbound.listener.LanguageListener;
import com.uxplima.auth.paper.adapter.inbound.screen.ConnectingLocales;
import com.uxplima.auth.paper.adapter.inbound.staff.Staff;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.CommandMessages;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.scheduler.TaskHandle;
import com.uxplima.uxmlib.storage.sql.Database;
import com.uxplima.uxmlib.text.language.FilePlayerLanguages;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageFiles;
import com.uxplima.uxmlib.text.language.LanguageResolver;
import com.uxplima.uxmlib.text.language.LanguageServices;
import com.uxplima.uxmlib.text.language.Languages;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import com.uxplima.uxmlib.text.style.ThemeFiles;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;

/**
 * The composition root. Every object in the plugin is built here and handed its collaborators through a
 * constructor. Nothing reaches for a singleton, and nothing calls {@code JavaPlugin.getInstance()}, which
 * is what lets every other class be built in a test with a fake.
 *
 * <p>This is the only class that names {@link JavaPlugin}.
 */
final class PluginModule {

    private static final String MESSAGES = "messages";

    /**
     * The folder beside the plugins that our suite keeps its one theme file in. The library asks for the
     * name rather than choosing it, because which plugins form a suite is our convention and not its
     * business.
     */
    private static final String THEME_FOLDER = "uxmTheme";

    /** How often the choices a player made reach the disk. A leave writes nothing on its own. */
    private static final Duration SAVE_PERIOD = Duration.ofMinutes(5);

    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final ScreenFile screens;

    private @Nullable Styler styler;

    private @Nullable Messages messages;

    // The files that were found the last time they were read. A reload reads them again, so a language file
    // written while the server runs becomes a choice without a restart.
    private volatile Languages languages;

    private @Nullable FilePlayerLanguages store;

    private @Nullable TaskHandle saveTask;

    private @Nullable Database database;

    private @Nullable AuthGraph auth;

    PluginModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Path dataFolder = plugin.getDataFolder().toPath();
        extractDefaults(dataFolder);
        this.settings = new PluginSettings(dataFolder.resolve("config.conf"));
        this.screens = new ScreenFile(dataFolder.resolve("screens.conf"));
        this.languages = readLanguages();
    }

    /** Build the graph and register everything that the server must see. */
    void wire() {
        Scheduler scheduler = new PaperScheduler(plugin);
        reportProblems();
        this.database = openDatabase();

        FilePlayerLanguages chosen =
                FilePlayerLanguages.loadedFrom(plugin.getDataFolder().toPath().resolve("languages.conf"));
        this.store = chosen;

        // The resolver reads the settings on every call, so a reload changes what a player reads without
        // anything here being built again. uxmLang is asked first when a server runs it, and the lookup is a
        // service and not an import: this plugin works the same on a server that has never heard of it.
        LanguageResolver resolver =
                new LanguageResolver(() -> settings.current().language(), chosen, LanguageServices::registered);

        // A client in the configuration phase has no player yet; its screens are read in its language too.
        Messages messages = new Messages(
                styledCatalog(),
                new ConnectingLocales(
                        resolver, () -> settings.current().language(), chosen, LanguageServices::registered));
        this.messages = messages;
        settings.afterReload(this::reload);

        LanguageChoices choices = new LanguageChoices(
                this::foundLocales, resolver, settings.current().language());

        plugin.getServer().getPluginManager().registerEvents(new LanguageListener(resolver), plugin);
        // The store answers from memory and owes the disk a write. The write happens here, on a timer and
        // once more at shutdown, so a busy server never touches the disk on the thread that draws a message.
        this.saveTask = scheduler.asyncTimer(SAVE_PERIOD, SAVE_PERIOD, handle -> saveChoices());

        registerCommands(scheduler, messages, choices);
    }

    /**
     * What this plugin says about itself when an operator asks.
     *
     * <p>Asked for on every run rather than built once, so a reload that fixed a file is a doctor run that
     * says so. Every check is read only, which is why the branch runs the whole list on the lane.
     */
    private java.util.List<com.uxplima.uxmlib.health.HealthCheck> healthChecks() {
        return java.util.List.of(PluginHealth.languages(() -> foundLocales().size()), PluginHealth.storage(() -> {
            Database open = database;
            return open != null && open.ping();
        }));
    }

    /**
     * Give back everything that the server does not take back by itself. A reload disables and enables the
     * plugin again, so anything left open here is leaked once per reload.
     *
     * <p>Closed in the reverse order of {@link #wire()}: the timer first, so the last write is this one and
     * not a task that runs while the server is already tearing the plugin down.
     */
    void shutdown() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveChoices();
        store = null;
        if (auth != null) {
            auth.close();
            auth = null;
        }
        if (database != null) {
            database.close();
            database = null;
        }
    }

    /**
     * Open the database and bring its tables up to date.
     *
     * <p>Done at enable, before anything that reads an account is registered, so the first join finds the
     * tables. The migrations are read from the jar, and an operator never runs SQL by hand. The UUID scheme is
     * written down on the first start and held against every later one.
     */
    private Database openDatabase() {
        Database opened = AuthDatabase.open(settings.current().storage(), plugin.getName());
        try {
            new RecordedScheme(opened).check(settings.current().ownership().uuidScheme());
        } catch (RuntimeException refused) {
            opened.close();
            throw refused;
        }
        return opened;
    }

    /**
     * Write the language a player chose, when there is one to write.
     *
     * <p>A failed write must not stop a shutdown, so the reason goes to the console and the server carries
     * on. The operator needs to see it, because a lost choice is a player who has to choose again.
     */
    private void saveChoices() {
        FilePlayerLanguages open = store;
        if (open == null || !open.dirty()) {
            return;
        }
        try {
            open.save();
        } catch (RuntimeException unwritable) {
            plugin.getSLF4JLogger().warn("Cannot write the language store.", unwritable);
        }
    }

    private void registerCommands(Scheduler scheduler, Messages messages, LanguageChoices choices) {
        // commands.conf names every command. @FromConfig carries only a key, and this replacer
        // rewrites it into the @Command the DSL expects. A handler must never also declare
        // @Command: uxmLib keeps a declared annotation over a produced one, so the file would be
        // read and then ignored, which is worse than not reading it at all.
        ConfiguredCommands commands =
                ConfiguredCommands.load(plugin.getDataFolder().toPath().resolve("commands.conf"));
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .messages(CommandMessages.fromCatalogue(messages))
                .replacer(FromConfig.class, commands.replacer())
                .suggestions(LanguageSuggestions.KEY, new LanguageSuggestions(choices));

        AuthGraph graph = new AuthGraph(
                plugin,
                scheduler,
                settings,
                screens,
                Objects.requireNonNull(database, "database"),
                messages,
                styler(),
                commands);
        this.auth = graph;
        graph.start();
        registerPlayerCommands(graph, commands, resolvers, scheduler);

        registerAdminCommand(commands, resolvers, scheduler, messages, choices, graph.staff);
    }

    private void registerAdminCommand(
            ConfiguredCommands commands,
            ParamResolvers resolvers,
            Scheduler scheduler,
            Messages messages,
            LanguageChoices choices,
            Staff staff) {
        if (commands.isEnabled(AdminCommand.KEY, "uxmauth")) {
            AnnotatedCommands.register(
                    plugin,
                    new AdminCommand(
                            settings,
                            messages,
                            plugin.getPluginMeta().getVersion(),
                            scheduler,
                            this::healthChecks,
                            choices,
                            staff),
                    resolvers,
                    scheduler);
        }
    }

    private void registerPlayerCommands(
            AuthGraph graph, ConfiguredCommands commands, ParamResolvers resolvers, Scheduler scheduler) {
        registerIfEnabled(commands, LoginCommand.KEY, "login", graph.login, resolvers, scheduler);
        registerIfEnabled(commands, RegisterCommand.KEY, "register", graph.register, resolvers, scheduler);
        registerIfEnabled(
                commands, ChangePasswordCommand.KEY, "changepassword", graph.changePassword, resolvers, scheduler);
        registerIfEnabled(commands, LogoutCommand.KEY, "logout", graph.logout, resolvers, scheduler);
        registerIfEnabled(commands, ClaimCommand.KEY, "claim", graph.claim, resolvers, scheduler);
        registerIfEnabled(commands, PremiumCommand.KEY, "premium", graph.premium, resolvers, scheduler);
        registerIfEnabled(commands, SessionCommand.KEY, "session", graph.session, resolvers, scheduler);
        registerIfEnabled(commands, CaptchaCommand.KEY, "captcha", graph.captchaCommand, resolvers, scheduler);
        registerIfEnabled(commands, TwoFactorCommand.KEY, "2fa", graph.twoFactor, resolvers, scheduler);
        registerIfEnabled(commands, EmailCommand.KEY, "email", graph.email, resolvers, scheduler);
        registerIfEnabled(commands, RecoverCommand.KEY, "recover", graph.recover, resolvers, scheduler);
    }

    private void registerIfEnabled(
            ConfiguredCommands commands,
            String key,
            String fallbackName,
            Object handler,
            ParamResolvers resolvers,
            Scheduler scheduler) {
        if (commands.isEnabled(key, fallbackName)) {
            AnnotatedCommands.register(plugin, handler, resolvers, scheduler);
        }
    }

    /** The languages this plugin found, which is the set a player may choose from. */
    private Set<Locale> foundLocales() {
        return languages.locales();
    }

    /**
     * The words, read from the folder rather than from a list in this class.
     *
     * <p>A plugin gains a language by gaining a file. Nothing here names {@code en} or {@code tr}, so an
     * operator who writes {@code messages_de.conf} has German and needs no new build.
     */
    private Languages readLanguages() {
        return Languages.load(
                plugin.getDataFolder().toPath().resolve(MESSAGES),
                settings.current().defaultLocale());
    }

    /**
     * Say once what is wrong with the files and the settings, and start anyway.
     *
     * <p>A file that cannot be read and a translation that is missing a key are both survivable: the catalog
     * falls back through the default language to the text each key carries. An operator still has to be told,
     * because a silent fallback looks like a plugin that ignores their file.
     */
    private void reportProblems() {
        for (String problem : languages.problems()) {
            plugin.getSLF4JLogger().warn(problem);
        }
        settings.current().protection().problems().forEach(plugin.getSLF4JLogger()::warn);
    }

    /**
     * The palette, read once. The menus need it for the parts of a tile that are not text, and the message
     * catalog needs it for every line, so it is built here and handed to both.
     */
    private Styler styler() {
        if (styler == null) {
            styler = new Styler(loadTheme());
        }
        return styler;
    }

    private Theme loadTheme() {
        Path dataFolder = plugin.getDataFolder().toPath();
        try {
            return ThemeFiles.load(ThemeFiles.shared(dataFolder, THEME_FOLDER), ThemeFiles.own(dataFolder));
        } catch (ConfigurateException | IllegalArgumentException unreadableTheme) {
            // A broken palette must not stop a server. The shipped colours are always correct.
            plugin.getSLF4JLogger().warn("Cannot read theme.conf. The shipped palette is used.", unreadableTheme);
            return Theme.defaults();
        }
    }

    /**
     * The words, painted. The style pass covers the keys the code knows and the paths a file adds.
     *
     * <p>A plugin can build a key at run time: one lore line of several, one cosmetic of a pack, one lobby of
     * a network. A path like that is in no enum, so without the file entries the line an operator wrote
     * themselves would be the one line that reached a player with its tokens unexpanded.
     */
    private MessageCatalog styledCatalog() {
        Map<Locale, Map<String, String>> entries = languages.entries();
        return styler().style(
                        languages.catalog(),
                        List.of(Keys.values()),
                        entries,
                        settings.current().defaultLocale());
    }

    /**
     * Re-read the words and the palette, and hand them to the {@link Messages} that every port already holds.
     *
     * <p>Both go in even though the catalog is usually the only one that changed: a reload that repaints half
     * of what a player sees is worse than one that repaints none. The locale source is not replaced, because
     * it reads the settings on every call and the new snapshot is already in place.
     *
     * <p>The styler takes the new palette rather than being replaced, because every menu holds this one
     * instance and asks it for the theme each time it builds a tile. Replacing it would repaint the chat and
     * leave the menus on the old colours.
     */
    private void reload() {
        if (messages == null) {
            return;
        }
        this.languages = readLanguages();
        reportProblems();
        screens.reload();
        styler().reload(loadTheme());
        messages.reload(styledCatalog());
    }

    /**
     * Give an operator who already has the file the keys this release added.
     *
     * <p>A plugin that only writes its config on the first run hands every later knob to a new server and
     * to no existing one: the operator who has run it longest is the one who never gets it, and they find
     * out by reading a changelog. The merge adds what is absent and changes no value anybody wrote, and it
     * keeps the file as it was beside it before it renders the document again.
     */
    private void mergeShippedConfig(Path dataFolder) {
        Path file = dataFolder.resolve("config.conf");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            if (PluginSettings.bringUpToDate(file, getClass().getClassLoader())) {
                plugin.getSLF4JLogger()
                        .info("config.conf has the keys this release adds. Nothing you wrote changed, and the"
                                + " file as it was is beside it as config.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            // A merge that cannot run must not stop a server: the file the operator has still loads, and a
            // key they are missing is a knob they do not have rather than a server that does not start.
            plugin.getSLF4JLogger().warn("config.conf could not be brought up to date: {}", unreadable.getMessage());
        }
    }

    /** The same promise as the configuration's: a release's new screen keys reach a server installed before it. */
    private void mergeShippedScreens(Path dataFolder) {
        Path file = dataFolder.resolve("screens.conf");
        try {
            if (ScreenFile.bringUpToDate(file, getClass().getClassLoader())) {
                plugin.getSLF4JLogger()
                        .info("screens.conf has the keys this release adds. Nothing you wrote changed, and the"
                                + " file as it was is beside it as screens.conf.bak.");
            }
        } catch (RuntimeException unreadable) {
            plugin.getSLF4JLogger().warn("screens.conf could not be brought up to date: {}", unreadable.getMessage());
        }
    }

    /** Write the shipped defaults on first run. An existing file is never overwritten. */
    private void extractDefaults(Path dataFolder) {
        saveIfAbsent(dataFolder, "config.conf");
        mergeShippedConfig(dataFolder);
        saveIfAbsent(dataFolder, "screens.conf");
        mergeShippedScreens(dataFolder);
        saveIfAbsent(dataFolder, "commands.conf");
        saveIfAbsent(dataFolder, "passwords/common.txt");
        saveSharedTheme(dataFolder);
        // The language files are discovered in the jar rather than named in a list here. A plugin gains a
        // language by gaining a file, and a list is the one place that gets forgotten when it does.
        LanguageFiles.extractShipped(getClass().getClassLoader(), MESSAGES, dataFolder.resolve(MESSAGES));
        MailFiles.extractShipped(getClass().getClassLoader(), dataFolder.resolve(MailFiles.FOLDER));
    }

    /**
     * Write the shipped palette where every plugin of ours reads it, on first run.
     *
     * <p>It goes beside this plugin's folder rather than inside it, so the second plugin a server installs
     * finds the file the first one wrote and the whole suite reads one look. An existing file is never
     * touched, whoever wrote it.
     */
    private void saveSharedTheme(Path dataFolder) {
        Path shared = ThemeFiles.shared(dataFolder, THEME_FOLDER);
        if (Files.isRegularFile(shared)) {
            return;
        }
        try (InputStream shipped = plugin.getResource("theme.conf")) {
            if (shipped == null) {
                return;
            }
            Path folder = shared.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            Files.copy(shipped, shared);
        } catch (IOException unwritable) {
            plugin.getSLF4JLogger()
                    .warn("Cannot write the theme to {}. The shipped palette is used.", shared, unwritable);
        }
    }

    private void saveIfAbsent(Path dataFolder, String resource) {
        if (Files.isRegularFile(dataFolder.resolve(resource))) {
            return;
        }
        plugin.saveResource(resource, false);
    }
}
