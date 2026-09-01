package com.uxplima.template.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.template.adapter.inbound.command.AdminCommand;
import com.uxplima.template.adapter.inbound.command.CommandLines;
import com.uxplima.template.adapter.inbound.command.ConfiguredCommands;
import com.uxplima.template.adapter.inbound.command.ExampleCommand;
import com.uxplima.template.adapter.inbound.command.FromConfig;
import com.uxplima.template.adapter.outbound.InMemoryExampleRepository;
import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.CreateExample;
import com.uxplima.template.application.message.Keys;
import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
import com.uxplima.uxmlib.text.style.Styler;
import com.uxplima.uxmlib.text.style.Theme;
import com.uxplima.uxmlib.text.style.ThemeFiles;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The composition root. Every object in the plugin is built here and handed its collaborators through a
 * constructor. Nothing reaches for a singleton, and nothing calls {@code JavaPlugin.getInstance()}, which
 * is what lets every other class be built in a test with a fake.
 *
 * <p>This is the only class that names {@link JavaPlugin}.
 */
final class PluginModule {

    private static final String[] SHIPPED_LOCALES = {"en", "tr"};

    private final JavaPlugin plugin;
    private final PluginSettings settings;

    private @Nullable Styler styler;

    private @Nullable Messages messages;

    PluginModule(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        Path dataFolder = plugin.getDataFolder().toPath();
        extractDefaults(dataFolder);
        this.settings = new PluginSettings(dataFolder.resolve("config.conf"));
    }

    /** Build the graph and register everything that the server must see. */
    void wire() {
        Scheduler scheduler = new PaperScheduler(plugin);
        Messages messages = buildMessages();
        this.messages = messages;
        settings.afterReload(this::reloadMessages);

        ExampleRepository repository = new InMemoryExampleRepository();
        CreateExample createExample =
                new CreateExample(repository, settings.current().exampleLimit());

        // commands.conf names every command. @FromConfig carries only a key, and this replacer
        // rewrites it into the @Command the DSL expects. A handler must never also declare
        // @Command: uxmLib keeps a declared annotation over a produced one, so the file would be
        // read and then ignored, which is worse than not reading it at all.
        ConfiguredCommands commands =
                ConfiguredCommands.load(plugin.getDataFolder().toPath().resolve("commands.conf"));
        ParamResolvers resolvers = ParamResolvers.withDefaults()
                .messages(new CommandLines(messages))
                .replacer(FromConfig.class, commands.replacer());

        if (commands.isEnabled(ExampleCommand.KEY, "example")) {
            AnnotatedCommands.register(
                    plugin, new ExampleCommand(createExample, repository, messages), resolvers, scheduler);
        }
        if (commands.isEnabled(AdminCommand.KEY, "uxmtemplate")) {
            AnnotatedCommands.register(
                    plugin,
                    new AdminCommand(settings, messages, plugin.getPluginMeta().getVersion()),
                    resolvers,
                    scheduler);
        }
    }

    /**
     * Give back everything that the server does not take back by itself. A reload disables and
     * enables the plugin again, so anything left open here is leaked once per reload.
     *
     * <p>The template opens nothing, so this method is empty. It is here because the plugin that
     * opens a database, installs the GUI listener or starts a repeating task needs one obvious
     * place to close it, and a place that has to be invented is a place that gets forgotten.
     * Close in the reverse order of {@link #wire()}: a task that touches the database is
     * cancelled before the database is closed.
     */
    void shutdown() {
        // Nothing is open yet. Close your database, your GUI listener and your timers here.
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
            return ThemeFiles.load(ThemeFiles.shared(dataFolder), dataFolder.resolve("theme.conf"));
        } catch (ConfigurateException | IllegalArgumentException unreadableTheme) {
            // A broken palette must not stop a server. The shipped colours are always correct.
            plugin.getSLF4JLogger().warn("Cannot read theme.conf. The shipped palette is used.", unreadableTheme);
            return Theme.defaults();
        }
    }

    private Messages buildMessages() {
        return new Messages(loadCatalog(), LocaleSource.ofDefault(defaultLocale()));
    }

    /** The locale a player without one of their own reads, as {@code config.conf} names it. */
    private Locale defaultLocale() {
        return Locale.forLanguageTag(settings.current().locale());
    }

    /**
     * Re-read the words and the palette, and hand them to the {@link Messages} that every port already holds.
     *
     * <p>The locale source travels with them, because an operator who edits {@code locale} changes which
     * language a player without one of their own reads. All four parts go in even though the catalog is
     * usually the only one that changed: a reload that repaints half of what a player sees is worse than one
     * that repaints none. See UI-STYLE.md section 14.2.
     *
     * <p>The styler takes the new palette rather than being replaced, because every menu holds this one
     * instance and asks it for the theme each time it builds a tile. Replacing it would repaint the chat and
     * leave the menus on the old colours.
     */
    private void reloadMessages() {
        if (messages == null) {
            return;
        }
        styler().reload(loadTheme());
        messages.reload(loadCatalog(), LocaleSource.ofDefault(defaultLocale()), Map.of(), TagResolver.empty());
    }

    private MessageCatalog loadCatalog() {
        Path messagesFolder = plugin.getDataFolder().toPath().resolve("messages");
        Map<Locale, ConfigurationNode> trees = new HashMap<>();

        for (String tag : SHIPPED_LOCALES) {
            Path file = messagesFolder.resolve("messages_" + tag + ".conf");
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                trees.put(
                        Locale.forLanguageTag(tag),
                        HoconConfigurationLoader.builder().path(file).build().load());
            } catch (IOException unreadableCatalog) {
                // A catalog that cannot be read must not stop the plugin: every key carries its own
                // default, so the locale simply falls back. The operator needs the reason in the console.
                plugin.getSLF4JLogger().warn("Cannot read the message catalog {}", file, unreadableCatalog);
            }
        }

        Locale defaultLocale = defaultLocale();
        MessageCatalog catalog = MessageCatalogLoader.fromNodes(trees, defaultLocale);
        Map<Locale, Map<String, String>> files = new HashMap<>();
        for (var entry : trees.entrySet()) {
            files.put(entry.getKey(), flatten(entry.getValue()));
        }
        MessageCatalog styled = styler().style(catalog, List.of(Keys.values()), files, defaultLocale);
        return styled;
    }

    /**
     * Every path a language file holds, flattened to {@code a.b.c -> template}.
     *
     * <p>The style pass covers what the key enum knows and what the file adds. A plugin can build a key at
     * run time: one lore line of several, one cosmetic of a pack, one lobby of a network. A path like that is
     * in no enum, so without this the line an operator wrote themselves would be the one line that reached a
     * player with its tokens unexpanded.
     */
    private static Map<String, String> flatten(ConfigurationNode node) {
        Map<String, String> flat = new LinkedHashMap<>();
        collect(node, "", flat);
        return flat;
    }

    private static void collect(ConfigurationNode node, String path, Map<String, String> into) {
        if (node.isMap()) {
            for (var child : node.childrenMap().entrySet()) {
                String childPath = path.isEmpty() ? String.valueOf(child.getKey()) : path + "." + child.getKey();
                collect(child.getValue(), childPath, into);
            }
            return;
        }
        String template = node.getString();
        if (template != null && !path.isEmpty()) {
            into.put(path, template);
        }
    }

    /** Write the shipped defaults on first run. An existing file is never overwritten. */
    private void extractDefaults(Path dataFolder) {
        saveIfAbsent(dataFolder, "config.conf");
        saveIfAbsent(dataFolder, "commands.conf");
        saveSharedTheme(dataFolder);
        for (String tag : SHIPPED_LOCALES) {
            saveIfAbsent(dataFolder, "messages/messages_" + tag + ".conf");
        }
    }

    /**
     * Write the shipped palette where every plugin of ours reads it, on first run.
     *
     * <p>It goes beside this plugin's folder rather than inside it, so the second plugin a server installs
     * finds the file the first one wrote and the whole suite reads one look. An existing file is never
     * touched, whoever wrote it.
     */
    private void saveSharedTheme(Path dataFolder) {
        Path shared = ThemeFiles.shared(dataFolder);
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
