package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.auth.paper.adapter.inbound.account.CrackedCommand;
import com.uxplima.auth.paper.adapter.inbound.account.UnregisterCommand;
import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;

/**
 * Every label a player may type for one of the password and code commands: each name, alias and word for one language
 * that {@code commands.conf} gives them, in lower case.
 *
 * <p>The hold lets these through for a waiting player, and the log filter hides what follows them.
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

    public static Set<String> of(ConfiguredCommands commands) {
        Objects.requireNonNull(commands, "commands");
        Set<String> labels = new LinkedHashSet<>();
        for (Class<?> command : COMMANDS) {
            FromConfig declared = Objects.requireNonNull(command.getAnnotation(FromConfig.class), command.getName());
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
