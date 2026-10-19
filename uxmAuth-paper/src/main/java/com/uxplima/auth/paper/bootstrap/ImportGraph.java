package com.uxplima.auth.paper.bootstrap;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;

import com.uxplima.auth.adapter.outbound.AuthDatabase;
import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.adapter.outbound.ImportLayout;
import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.adapter.outbound.SqlFactors;
import com.uxplima.auth.adapter.outbound.StartupImport;
import com.uxplima.auth.adapter.outbound.TableImport;
import com.uxplima.auth.adapter.outbound.premium.PremiumSources;
import com.uxplima.auth.application.migrate.MarkPremium;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.premium.PremiumLookup;
import com.uxplima.auth.paper.adapter.outbound.migrate.YamlImport;
import com.uxplima.uxmlib.storage.sql.Database;

/** Runs the import config.conf names, on the start thread, before the door opens. */
final class ImportGraph {

    private ImportGraph() {}

    static void run(JavaPlugin plugin, PluginSettings settings, Database database) {
        PluginSettings.Snapshot boot = settings.current();
        PluginSettings.Importing importing = boot.importing();
        if (importing.accounts().source().isEmpty()
                && importing.premium().source().isEmpty()) {
            return;
        }
        Path folder = plugin.getDataFolder().toPath().toAbsolutePath();
        AuthSettings.Network network = boot.network();
        // Sealed as the rest of the plugin seals, so an imported authenticator secret opens at the next login.
        SqlFactors factors = new SqlFactors(
                database, FactorGraph.seal(network.enabled(), network.secret(), folder), Clock.systemUTC());
        try (HttpClient web = HttpClient.newHttpClient()) {
            StartupImport.run(
                    importing,
                    folder.resolve(ImportLayout.FOLDER),
                    sources(folder, names(plugin.getServer())),
                    StartupImport.importer(database, factors, settings),
                    marker(database, web, settings),
                    work -> AuthDatabase.importing(database, boot.storage(), plugin.getName(), work),
                    plugin.getLogger()::info);
        }
    }

    /** A file source is read from the plugins folder, and a table source from the database it was named with. */
    private static Function<StartupImport.Named, ImportSource> sources(
            Path folder, Function<UUID, Optional<String>> names) {
        Path plugins = Objects.requireNonNull(folder.getParent(), "the data folder has no parent");
        return named -> named.layout().kind() == ImportLayout.Kind.YAML
                ? new YamlImport(named.layout(), plugins, names)
                : TableImport.open(named.layout(), named.address(), names);
    }

    /** The premium list asks the sources the door asks, when it gives one Mojang id to two names. */
    private static MarkPremium marker(Database database, HttpClient web, PluginSettings settings) {
        Clock clock = Clock.systemUTC();
        PremiumLookup lookup = new PremiumLookup(
                new PremiumSources(web, () -> settings.current().premium(), clock)::ordered,
                () -> settings.current().premium().keepAnswers(),
                clock);
        return StartupImport.marker(database, lookup::lookup, settings);
    }

    /** The name the server last saw a player under, for a source that keeps only ids. */
    private static Function<UUID, Optional<String>> names(Server server) {
        return id -> Optional.ofNullable(server.getOfflinePlayer(id).getName());
    }
}
