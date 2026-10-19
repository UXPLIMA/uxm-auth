package com.uxplima.auth.paper.adapter.outbound.world;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uxplima.auth.adapter.outbound.AuthSettings;
import com.uxplima.auth.domain.premium.ConflictStrategy;
import com.uxplima.auth.domain.premium.MarkedName;
import com.uxplima.auth.domain.premium.NameRule;

/**
 * Paper refuses a name with any character but letters, digits and the underscore while its
 * {@code perform-username-validation} is on. A mark this plugin puts in a name is then refused at the door, so
 * the start names the mark and the setting that lets it through.
 */
public final class NameMarkCheck {

    private static final Pattern SETTING =
            Pattern.compile("(?m)^\\s*perform-username-validation\\s*:\\s*(true|false)\\s*$");

    private NameMarkCheck() {}

    /** Whether Paper checks names, by its global file under the server's root; a file that does not say does. */
    public static boolean paperChecksNames(Path serverRoot) {
        Path file = serverRoot.resolve("config").resolve("paper-global.yml");
        try {
            Matcher found = SETTING.matcher(Files.readString(file, StandardCharsets.UTF_8));
            return !found.find() || Boolean.parseBoolean(found.group(1));
        } catch (IOException unreadable) {
            return true;
        }
    }

    /** The console line for a mark in use that Paper refuses while it checks names; empty when there is none. */
    public static Optional<String> warning(boolean paperChecks, AuthSettings.Ownership ownership) {
        if (!paperChecks) {
            return Optional.empty();
        }
        String domainMark = ownership.crackedMark().mark();
        if (ownership.rule() == NameRule.DOMAIN && !MarkedName.paperAccepts(domainMark)) {
            return Optional.of(line("ownership.domains.cracked-mark", domainMark));
        }
        String renameMark = ownership.renameMark();
        if (ownership.conflict() == ConflictStrategy.RENAME && !MarkedName.paperAccepts(renameMark)) {
            return Optional.of(line("ownership.rename-mark", renameMark));
        }
        return Optional.empty();
    }

    private static String line(String key, String mark) {
        return key + " is \"" + mark + "\", a character Paper refuses in a name while"
                + " unsupported-settings.perform-username-validation is on in config/paper-global.yml. A player with"
                + " that mark is refused at the door. Turn the setting off, or use letters, digits and the underscore.";
    }
}
