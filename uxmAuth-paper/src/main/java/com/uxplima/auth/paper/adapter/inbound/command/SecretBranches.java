package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmlib.command.annotation.ConfiguredCommands;

/**
 * The branches of the admin command that carry a password, as staff setting one: every label of the command and
 * every word of those branches, read from commands.conf, so the log filter hides what follows a renamed one too.
 */
public final class SecretBranches {

    /** The keys of the branches whose arguments hold a password. */
    static final List<String> BRANCHES = List.of("setpassword", "forceregister");

    private SecretBranches() {}

    /** Every name, alias and word for one language of the admin command, in lower case. */
    public static Set<String> labels(ConfiguredCommands commands) {
        return words(Objects.requireNonNull(commands, "commands").entryOf(AdminCommand.KEY, AdminCommand.KEY));
    }

    /** Every word of the password-carrying branches, in lower case. */
    public static Set<String> words(ConfiguredCommands commands) {
        Objects.requireNonNull(commands, "commands");
        Set<String> words = new LinkedHashSet<>();
        for (String branch : BRANCHES) {
            words.addAll(words(commands.entryOf(ConfiguredCommands.branchKey(AdminCommand.KEY, branch), branch)));
        }
        return Set.copyOf(words);
    }

    private static Set<String> words(ConfiguredCommands.Entry entry) {
        Set<String> words = new LinkedHashSet<>();
        words.add(entry.name().toLowerCase(Locale.ROOT));
        entry.aliases().forEach(alias -> words.add(alias.toLowerCase(Locale.ROOT)));
        entry.localizedAliases()
                .values()
                .forEach(each -> each.forEach(word -> words.add(word.toLowerCase(Locale.ROOT))));
        return Set.copyOf(words);
    }
}
