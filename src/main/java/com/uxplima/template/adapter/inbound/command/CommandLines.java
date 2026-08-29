package com.uxplima.template.adapter.inbound.command;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.command.annotation.CommandMessages;
import com.uxplima.uxmlib.common.Durations;
import com.uxplima.uxmlib.text.message.MessageKey;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The lines the command layer writes, worded and painted by this plugin.
 *
 * <p>uxmLib produces text that no catalog of ours would otherwise hold: the help page, the rejection of a bad
 * argument, the cooldown notice. Left alone that text is English and it is painted in vanilla colours, which
 * makes it the one screen in the plugin that is not ours, and the one no style guard can catch: it lives in a
 * jar rather than in {@code src/main/resources}, so nothing that reads this repository can see it.
 *
 * <p>Every method here answers with a key from our own catalog, so these lines are translated with the rest
 * and the palette owns them. A value that comes from outside (a command, a description, what the sender
 * typed) is inserted unparsed: it may hold angle brackets, and a usage line like {@code /tags set <name>}
 * must be shown and never obeyed.
 */
public final class CommandLines implements CommandMessages {

    private final Messages messages;

    public CommandLines(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public Component playerOnly(Locale locale) {
        return line(Keys.COMMAND_PLAYER_ONLY, locale);
    }

    @Override
    public Component invalidValue(Locale locale, String argument, String input, String reason) {
        if (reason.isEmpty()) {
            return line(
                    Keys.COMMAND_INVALID_VALUE,
                    locale,
                    Placeholder.unparsed("argument", argument),
                    Placeholder.unparsed("input", input));
        }
        return line(
                Keys.COMMAND_INVALID_VALUE_WHY,
                locale,
                Placeholder.unparsed("argument", argument),
                Placeholder.unparsed("input", input),
                Placeholder.unparsed("reason", reason));
    }

    @Override
    public Component notOneOf(Locale locale, String argument, String input, List<String> allowed) {
        return line(
                Keys.COMMAND_NOT_ONE_OF,
                locale,
                Placeholder.unparsed("argument", argument),
                Placeholder.unparsed("input", input),
                Placeholder.unparsed("allowed", String.join(", ", allowed)));
    }

    @Override
    public Component invalidArgument(Locale locale, String detail) {
        return detail.isEmpty()
                ? line(Keys.COMMAND_BAD_ARGUMENT, locale)
                : line(Keys.COMMAND_INVALID_ARGUMENT, locale, Placeholder.unparsed("detail", detail));
    }

    @Override
    public Component internalError(Locale locale) {
        return line(Keys.COMMAND_INTERNAL_ERROR, locale);
    }

    @Override
    public Component onCooldown(Locale locale, Duration remaining) {
        return line(Keys.COMMAND_ON_COOLDOWN, locale, Placeholder.unparsed("time", Durations.format(remaining)));
    }

    @Override
    public Component helpHeader(Locale locale, String command, int page, int pages) {
        return line(
                Keys.COMMAND_HELP_HEADER,
                locale,
                Placeholder.unparsed("command", command),
                Placeholder.unparsed("page", String.valueOf(page)),
                Placeholder.unparsed("pages", String.valueOf(pages)));
    }

    @Override
    public Component helpCommand(Locale locale, String command) {
        return line(Keys.COMMAND_HELP_COMMAND, locale, Placeholder.unparsed("command", command));
    }

    @Override
    public Component helpSeparator(Locale locale) {
        return line(Keys.COMMAND_HELP_SEPARATOR, locale);
    }

    @Override
    public Component helpDescription(Locale locale, String description) {
        return line(Keys.COMMAND_HELP_DESCRIPTION, locale, Placeholder.unparsed("description", description));
    }

    @Override
    public Component helpFillHint(Locale locale) {
        return line(Keys.COMMAND_HELP_FILL_HINT, locale);
    }

    @Override
    public Component helpPageHint(Locale locale, int page) {
        return line(Keys.COMMAND_HELP_PAGE_HINT, locale, Placeholder.unparsed("page", String.valueOf(page)));
    }

    /**
     * One line, taken from the catalog the facade currently reads.
     *
     * <p>The template is asked for by locale rather than by viewer, because that is what the command layer
     * hands us. The catalog is already styled, so this is a parse and nothing more.
     */
    private Component line(MessageKey key, Locale locale, TagResolver... resolvers) {
        return MiniMessage.miniMessage().deserialize(messages.catalog().template(key, locale), resolvers);
    }
}
