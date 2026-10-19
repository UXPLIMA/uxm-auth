package com.uxplima.auth.adapter.outbound;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.config.HoconConfig;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

/**
 * The words a player types for one command, read straight from commands.conf. A server builds its command tree
 * from the file through uxmLib; the proxy's waiting world has no tree, and reads the same words here.
 */
public final class CommandFile {

    private CommandFile() {}

    /**
     * Adds to an operator's file every command a release added, keeping what they wrote.
     *
     * <p>A command the file does not name still works under its own name. What a file without it loses is the
     * promise that every command can be renamed: an operator cannot rename a command their file never shows,
     * and the readers of another language never get its words.
     */
    public static boolean bringUpToDate(Path file, ClassLoader resources) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(resources, "resources");
        if (!Files.isRegularFile(file)) {
            return false;
        }
        return HoconConfig.load(file).mergeDefaults("commands.conf", resources);
    }

    /** Every word for the command under this key, in lower case: none when it is disabled or absent. */
    public static Set<String> labels(Path file, String key) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(key, "key");
        ConfigurationNode command = HoconConfig.load(file).nodeAt("commands", key);
        Set<String> labels = new LinkedHashSet<>();
        if (command.virtual() || !command.node("enabled").getBoolean(true)) {
            return labels;
        }
        add(labels, command.node("name").getString(key));
        try {
            for (String alias : command.node("aliases").getList(String.class, java.util.List.of())) {
                add(labels, alias);
            }
            for (ConfigurationNode language :
                    command.node("localized-aliases").childrenMap().values()) {
                for (String word : language.getList(String.class, java.util.List.of())) {
                    add(labels, word);
                }
            }
        } catch (SerializationException unreadable) {
            throw new IllegalArgumentException(
                    "commands." + key + " holds a list that is not a list of words: " + unreadable.getMessage(),
                    unreadable);
        }
        return labels;
    }

    private static void add(Set<String> labels, String word) {
        String clean = word.trim().toLowerCase(Locale.ROOT);
        if (!clean.isEmpty()) {
            labels.add(clean);
        }
    }
}
