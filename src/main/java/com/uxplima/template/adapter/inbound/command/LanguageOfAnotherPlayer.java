package com.uxplima.template.adapter.inbound.command;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.command.CommandSender;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageNames;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * What an operator does to another player's language: set it, or clear it.
 *
 * <p>The choice is the player's own on every plugin of ours on the server, so this sets it once for all of
 * them. A player who is away has it applied when they join. The console may do it, so nothing here needs a
 * player of its own.
 *
 * <p>It is one class rather than two methods on the command, because every plugin of ours has the same two
 * branches under its own command and the same test proves them.
 */
public final class LanguageOfAnotherPlayer {

    private final Messages messages;

    private final LanguageChoices choices;

    private final Supplier<Optional<Locale>> forced;

    /** @param forced the language the server forces now, read on every call so a reload is seen */
    public LanguageOfAnotherPlayer(Messages messages, LanguageChoices choices, Supplier<Optional<Locale>> forced) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.choices = Objects.requireNonNull(choices, "choices");
        this.forced = Objects.requireNonNull(forced, "forced");
    }

    /** Set the language the player called {@code name} reads. */
    public void set(CommandSender operator, String name, String language) {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(language, "language");
        Optional<Locale> wanted = choices.parse(language);
        if (wanted.isEmpty()) {
            messages.send(
                    operator,
                    Keys.LANGUAGE_UNKNOWN,
                    Text.placeholder("input", language),
                    Text.component("languages", available(operator)));
            return;
        }
        Locale locale = wanted.get();
        TagResolver named = Text.placeholder("language", LanguageNames.of(locale));
        switch (choices.named(name)) {
            case LanguageChoices.Named.Unknown unknown -> sayNobody(operator, unknown);
            case LanguageChoices.Named.Online online -> {
                choices.choose(online.player().getUniqueId(), locale);
                messages.send(online.player(), Keys.LANGUAGE_CHANGED, named);
                messages.send(
                        operator,
                        Keys.LANGUAGE_SET_PLAYER,
                        playerTag(online.player().getName()),
                        named);
                sayWhenForced(operator);
            }
            case LanguageChoices.Named.Away away -> {
                choices.choose(away.id(), locale);
                messages.send(operator, Keys.LANGUAGE_SET_PLAYER_AWAY, playerTag(away.name()), named);
                sayWhenForced(operator);
            }
        }
    }

    /** Clear the language the player called {@code name} chose, so the server decides for them again. */
    public void reset(CommandSender operator, String name) {
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(name, "name");
        switch (choices.named(name)) {
            case LanguageChoices.Named.Unknown unknown -> sayNobody(operator, unknown);
            case LanguageChoices.Named.Online online -> {
                choices.reset(online.player().getUniqueId());
                messages.send(online.player(), Keys.LANGUAGE_RESET);
                messages.send(
                        operator,
                        Keys.LANGUAGE_RESET_PLAYER,
                        playerTag(online.player().getName()));
            }
            case LanguageChoices.Named.Away away -> {
                choices.reset(away.id());
                messages.send(operator, Keys.LANGUAGE_RESET_PLAYER_AWAY, playerTag(away.name()));
            }
        }
    }

    private void sayNobody(CommandSender operator, LanguageChoices.Named.Unknown unknown) {
        messages.send(operator, Keys.LANGUAGE_UNKNOWN_PLAYER, Text.placeholder("input", unknown.typed()));
    }

    /** A choice is kept while a language is forced, and the operator is told it waits behind the force. */
    private void sayWhenForced(CommandSender operator) {
        forced.get()
                .ifPresent(language -> messages.send(
                        operator,
                        Keys.LANGUAGE_FORCED_ANYWAY,
                        Text.placeholder("language", LanguageNames.of(language))));
    }

    /** Every language this plugin found, in one line, with the separator the reader's language writes. */
    private Component available(Audience reader) {
        Component separator = messages.render(reader, Keys.LANGUAGE_SEPARATOR);
        return Component.join(
                JoinConfiguration.separator(separator),
                choices.available().stream()
                        .map(locale -> Component.text(LanguageNames.of(locale)))
                        .toList());
    }

    private static TagResolver playerTag(String name) {
        return Text.placeholder("player", name);
    }
}
