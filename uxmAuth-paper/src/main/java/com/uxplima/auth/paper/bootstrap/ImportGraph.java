package com.uxplima.auth.paper.bootstrap;

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
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.paper.adapter.outbound.migrate.YamlImport;
import com.uxplima.uxmlib.storage.sql.Database;

/** Runs the import config.conf names, on the start thread, before the door opens. */
final class ImportGraph {

    private ImportGraph() {}

    static void run(JavaPlugin plugin, PluginSettings settings, Database database) {
        PluginSettings.Snapshot boot = settings.current();
        if (boot.importing().source().isEmpty()) {
            return;
        }
        Path folder = plugin.getDataFolder().toPath().toAbsolutePath();
        AuthSettings.Network network = boot.network();
        // Sealed as the rest of the plugin seals, so an imported authenticator secret opens at the next login.
        SqlFactors factors = new SqlFactors(
                database, FactorGraph.seal(network.enabled(), network.secret(), folder), Clock.systemUTC());
        StartupImport.run(
                boot.importing(),
                folder.resolve(ImportLayout.FOLDER),
                sources(folder, boot.importing().address(), names(plugin.getServer())),
                StartupImport.importer(database, factors, settings),
                work -> AuthDatabase.importing(database, boot.storage(), plugin.getName(), work),
                plugin.getLogger()::info);
    }

    /** A file source is read from the plugins folder, and a table source from the database the settings name. */
    private static Function<ImportLayout, ImportSource> sources(
            Path folder, TableImport.Address address, Function<UUID, Optional<String>> names) {
        Path plugins = Objects.requireNonNull(folder.getParent(), "the data folder has no parent");
        return layout -> layout.kind() == ImportLayout.Kind.YAML
                ? new YamlImport(layout, plugins, names)
                : TableImport.open(layout, address, names);
    }

    /** The name the server last saw a player under, for a source that keeps only ids. */
    private static Function<UUID, Optional<String>> names(Server server) {
        return id -> Optional.ofNullable(server.getOfflinePlayer(id).getName());
    }
}
