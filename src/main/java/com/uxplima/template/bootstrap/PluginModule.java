package com.uxplima.template.bootstrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.template.adapter.inbound.command.AdminCommand;
import com.uxplima.template.adapter.inbound.command.ConfiguredCommands;
import com.uxplima.template.adapter.inbound.command.ExampleCommand;
import com.uxplima.template.adapter.inbound.command.FromConfig;
import com.uxplima.template.adapter.outbound.InMemoryExampleRepository;
import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.CreateExample;
import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.uxmlib.command.annotation.AnnotatedCommands;
import com.uxplima.uxmlib.command.annotation.ParamResolvers;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.message.LocaleSource;
import com.uxplima.uxmlib.text.message.MessageCatalog;
import com.uxplima.uxmlib.text.message.MessageCatalogLoader;
import com.uxplima.uxmlib.text.message.Messages;
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

        ExampleRepository repository = new InMemoryExampleRepository();
        CreateExample createExample =
                new CreateExample(repository, settings.current().exampleLimit());

        // commands.conf names every command. @FromConfig carries only a key, and this replacer
        // rewrites it into the @Command the DSL expects. A handler must never also declare
        // @Command: uxmLib keeps a declared annotation over a produced one, so the file would be
        // read and then ignored, which is worse than not reading it at all.
        ConfiguredCommands commands =
                ConfiguredCommands.load(plugin.getDataFolder().toPath().resolve("commands.conf"));
        ParamResolvers resolvers = ParamResolvers.withDefaults().replacer(FromConfig.class, commands.replacer());

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

    private Messages buildMessages() {
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

        Locale defaultLocale = Locale.forLanguageTag(settings.current().locale());
        MessageCatalog catalog = MessageCatalogLoader.fromNodes(trees, defaultLocale);
        return new Messages(catalog, LocaleSource.ofDefault(defaultLocale));
    }

    /** Write the shipped defaults on first run. An existing file is never overwritten. */
    private void extractDefaults(Path dataFolder) {
        saveIfAbsent(dataFolder, "config.conf");
        saveIfAbsent(dataFolder, "commands.conf");
        for (String tag : SHIPPED_LOCALES) {
            saveIfAbsent(dataFolder, "messages/messages_" + tag + ".conf");
        }
    }

    private void saveIfAbsent(Path dataFolder, String resource) {
        if (Files.isRegularFile(dataFolder.resolve(resource))) {
            return;
        }
        plugin.saveResource(resource, false);
    }
}
