package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.plugin.Plugin;

import com.uxplima.auth.paper.adapter.inbound.account.CrackedCommand;
import com.uxplima.auth.paper.adapter.inbound.account.UnregisterCommand;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;

/**
 * Every label a player may type for one of the password and code commands: each name, alias and word for one language
 * that {@code commands.conf} gives them, in lower case.
 *
 * <p>The log filter hides what follows every one of them, a command turned off included: a password typed after it
 * is still a password. The hold lets through only the labels of the commands that are on, and only where the server
 * runs this plugin's command under them.
 */
public final class PlayerCommandLabels {

    /**
     * The commands a waiting player uses: the password commands, the claim on a name they cannot log in to, the
     * answer to a captcha, and the second factor, whose codes the log must not keep either. The commands on a
     * player's own account carry a password or a code too; a waiting player who runs one is told to log in.
     */
    static final List<Class<?>> COMMANDS = List.of(
            LoginCommand.class,
            RegisterCommand.class,
            ChangePasswordCommand.class,
            LogoutCommand.class,
            ClaimCommand.class,
            CaptchaCommand.class,
            TwoFactorCommand.class,
            EmailCommand.class,
            RecoverCommand.class,
            UnregisterCommand.class,
            CrackedCommand.class);

    private PlayerCommandLabels() {}

    /** Every label of the commands, whether they are on or not. */
    public static Set<String> of(ConfiguredCommands commands) {
        return labels(commands, false);
    }

    /** The labels of the commands that {@code commands.conf} leaves on; one turned off is never registered. */
    public static Set<String> enabled(ConfiguredCommands commands) {
        return labels(commands, true);
    }

    /**
     * Whether the server runs one of this plugin's commands under a label. Another plugin may hold a bare label
     * first, a land plugin's {@code claim} or a chat plugin's alias, and the server then gives this plugin only its
     * namespaced one.
     */
    public static Predicate<String> ownedBy(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return label -> plugin.getServer().getCommandMap().getCommand(label) instanceof PluginIdentifiableCommand owned
                && owned.getPlugin() == plugin;
    }

    private static Set<String> labels(ConfiguredCommands commands, boolean onlyEnabled) {
        Objects.requireNonNull(commands, "commands");
        Set<String> labels = new LinkedHashSet<>();
        for (Class<?> command : COMMANDS) {
            FromConfig declared = Objects.requireNonNull(command.getAnnotation(FromConfig.class), command.getName());
            if (onlyEnabled && !commands.isEnabled(declared.value(), declared.fallbackName())) {
                continue;
            }
            ConfiguredCommands.Entry entry = commands.entryOf(declared.value(), declared.fallbackName());
            labels.add(entry.name().toLowerCase(Locale.ROOT));
            entry.aliases().forEach(alias -> labels.add(alias.toLowerCase(Locale.ROOT)));
            entry.localizedAliases()
                    .values()
                    .forEach(words -> words.forEach(word -> labels.add(word.toLowerCase(Locale.ROOT))));
        }
        return Set.copyOf(labels);
    }
}
