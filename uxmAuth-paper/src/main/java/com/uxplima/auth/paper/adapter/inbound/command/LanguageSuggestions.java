package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmlib.command.annotation.SuggestionSource;
import com.uxplima.uxmlib.text.language.LanguageChoices;

/**
 * Completes a language argument with the tags this server actually has.
 *
 * <p>A completion list is part of the interface, and one that offers what the command then refuses is a bad
 * interface. The list is the message files this plugin found, which is the same set the command accepts.
 */
public final class LanguageSuggestions implements SuggestionSource {

    /** The key the commands name this source under. */
    public static final String KEY = "languages";

    private final LanguageChoices choices;

    public LanguageSuggestions(LanguageChoices choices) {
        this.choices = Objects.requireNonNull(choices, "choices");
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        for (String tag : choices.suggestions(builder.getRemaining())) {
            builder.suggest(tag);
        }
        return builder.buildFuture();
    }
}
