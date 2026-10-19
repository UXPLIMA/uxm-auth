package com.uxplima.auth.paper.adapter.outbound.migrate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.uxplima.auth.adapter.outbound.ImportLayout;
import com.uxplima.auth.application.port.ImportSource;
import com.uxplima.auth.application.port.ImportedAccount;
import com.uxplima.auth.domain.hash.LegacyHashes;
import org.jspecify.annotations.Nullable;

/**
 * Reads another login plugin's YAML file: one entry per player id, the layout's columns naming the keys under it.
 * The file is only read. Bukkit reads YAML here, which is why this source is on Paper only.
 */
public final class YamlImport implements ImportSource {

    private final ImportLayout layout;
    private final Path file;
    private final Function<UUID, Optional<String>> names;

    /**
     * @param plugins the server's plugins folder, which the layout's file is found from
     * @param names the name the server knows a player's id by
     */
    public YamlImport(ImportLayout layout, Path plugins, Function<UUID, Optional<String>> names) {
        this.layout = Objects.requireNonNull(layout, "layout");
        this.file = Objects.requireNonNull(plugins, "plugins").resolve(layout.table());
        this.names = Objects.requireNonNull(names, "names");
        if (layout.kind() != ImportLayout.Kind.YAML) {
            throw new IllegalArgumentException(layout.display() + " is read from a table, not a YAML file");
        }
    }

    @Override
    public String key() {
        return layout.key();
    }

    @Override
    public Stream<ImportedAccount> read() {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(layout.display() + "'s file " + file + " is not there");
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
        List<ImportedAccount> rows = new ArrayList<>();
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection entry = yaml.getConfigurationSection(key);
            if (entry != null) {
                rows.add(row(key, entry));
            }
        }
        return rows.stream();
    }

    private ImportedAccount row(String key, ConfigurationSection entry) {
        ImportLayout.Columns columns = layout.columns();
        UUID id = idOf(key);
        String name = text(entry, columns.name());
        if (name == null) {
            name = id == null ? key : names.apply(id).orElse(id.toString());
        }
        String raw = text(entry, columns.password());
        return new ImportedAccount(
                name,
                id,
                null,
                raw == null
                        ? null
                        : LegacyHashes.rewrite(
                                layout.hashStyle(), raw, text(entry, columns.salt()), text(entry, columns.algorithm())),
                text(entry, columns.totp()),
                null,
                null,
                text(entry, columns.address()));
    }

    private static @Nullable UUID idOf(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException notAnId) {
            return null;
        }
    }

    private static @Nullable String text(ConfigurationSection entry, String column) {
        if (column.isEmpty()) {
            return null;
        }
        String value = entry.getString(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Nothing is held open between reads. */
    @Override
    public void close() {}
}
