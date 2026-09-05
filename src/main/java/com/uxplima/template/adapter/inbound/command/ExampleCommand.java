package com.uxplima.template.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.CreateExample;
import com.uxplima.template.application.message.Keys;
import com.uxplima.template.application.port.ExampleRepository;
import com.uxplima.template.domain.ExampleName;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.SuggestUsing;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageNames;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The inbound adapter for the example feature. An adapter translates: it turns a command into a call on a
 * use case, and it turns the outcome back into a message. It holds no rule of its own, which is why the
 * rules can be tested without a server.
 *
 * <p>The command name here is the default. An operator renames, aliases, or disables it in
 * {@code commands.conf}, and nothing in this class changes.
 */
@FromConfig(value = ExampleCommand.KEY, fallbackName = "example", description = "Manage your examples")
public final class ExampleCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "example";

    private final CreateExample createExample;
    private final ExampleRepository repository;
    private final Messages messages;
    private final LanguageChoices choices;
    private final PluginSettings settings;

    public ExampleCommand(
            CreateExample createExample,
            ExampleRepository repository,
            Messages messages,
            LanguageChoices choices,
            PluginSettings settings) {
        this.createExample = Objects.requireNonNull(createExample, "createExample");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.choices = Objects.requireNonNull(choices, "choices");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Subcommand("create")
    @Permission("uxmtemplate.example.create")
    @PlayerOnly
    public void create(Sender sender, @Arg("name") String rawName) {
        Player player = sender.player().orElseThrow();

        ExampleName name;
        try {
            name = new ExampleName(rawName);
        } catch (IllegalArgumentException invalidName) {
            messages.send(player, Keys.INVALID_NAME);
            return;
        }

        switch (createExample.run(player.getUniqueId(), name)) {
            case CreateExample.Outcome.Created created ->
                messages.send(
                        player,
                        Keys.EXAMPLE_CREATED,
                        Text.placeholder("name", created.fact().name().value()));
            case CreateExample.Outcome.AlreadyExists exists ->
                messages.send(
                        player,
                        Keys.EXAMPLE_EXISTS,
                        Text.placeholder("name", exists.name().value()));
            case CreateExample.Outcome.LimitReached limit ->
                messages.send(player, Keys.EXAMPLE_LIMIT, Text.placeholder("limit", String.valueOf(limit.limit())));
        }
    }

    @Subcommand("list")
    @Permission("uxmtemplate.example.list")
    @PlayerOnly
    public void list(Sender sender) {
        Player player = sender.player().orElseThrow();
        List<ExampleName> names = repository.findAll(player.getUniqueId());

        if (names.isEmpty()) {
            messages.send(player, Keys.EXAMPLE_LIST_EMPTY);
            return;
        }
        String joined = names.stream().map(ExampleName::value).collect(Collectors.joining(", "));
        messages.send(player, Keys.EXAMPLE_LIST, Text.placeholder("names", joined));
    }

    /**
     * Say what language this player reads, and what else this server has.
     *
     * <p>The branch sits under this plugin's own root and not under a bare {@code /lang}, because sixteen
     * plugins that each registered {@code /lang} would collide and the last one enabled would win. A server
     * that runs uxmLang gets one command for the whole network as well, and this branch still answers.
     */
    @Subcommand("lang")
    @Permission("uxmtemplate.language")
    @PlayerOnly
    public void language(Sender sender) {
        Player player = player(sender);
        messages.send(
                player,
                Keys.LANGUAGE_CURRENT,
                Text.placeholder("language", LanguageNames.of(messages.localeOf(player))));
        messages.send(player, Keys.LANGUAGE_CHOICES, Text.component("languages", available(player)));
    }

    /** Read this plugin in one language, whatever the client is set to. */
    @Subcommand("lang set")
    @Permission("uxmtemplate.language")
    @PlayerOnly
    public void setLanguage(Sender sender, @Arg("language") @SuggestUsing(LanguageSuggestions.KEY) String language) {
        Player player = player(sender);
        if (refusedBecauseForced(player)) {
            return;
        }
        Optional<Locale> wanted = choices.parse(language);
        if (wanted.isEmpty()) {
            messages.send(
                    player,
                    Keys.LANGUAGE_UNKNOWN,
                    Text.placeholder("input", language),
                    Text.component("languages", available(player)));
            return;
        }
        Locale locale = wanted.get();
        if (locale.equals(messages.localeOf(player))) {
            messages.send(player, Keys.LANGUAGE_ALREADY, Text.placeholder("language", LanguageNames.of(locale)));
            return;
        }
        choices.choose(player.getUniqueId(), locale);
        // Sent after the change, so the line that confirms it is already in the language they picked.
        messages.send(player, Keys.LANGUAGE_CHANGED, Text.placeholder("language", LanguageNames.of(locale)));
    }

    /** Follow the client again, as a player who never chose anything does. */
    @Subcommand("lang reset")
    @Permission("uxmtemplate.language")
    @PlayerOnly
    public void resetLanguage(Sender sender) {
        Player player = player(sender);
        if (refusedBecauseForced(player)) {
            return;
        }
        choices.reset(player.getUniqueId());
        messages.send(player, Keys.LANGUAGE_RESET);
    }

    /**
     * Every language this plugin found, in one line.
     *
     * <p>The separator is a message key rather than a comma in the code, because a language that reads right
     * to left, or one that punctuates a list differently, needs its own.
     */
    private Component available(Player player) {
        Component separator = messages.render(player, Keys.LANGUAGE_SEPARATOR);
        return Component.join(
                JoinConfiguration.separator(separator),
                choices.available().stream()
                        .map(locale -> Component.text(LanguageNames.of(locale)))
                        .toList());
    }

    /**
     * Say nothing can be chosen when the operator has forced one language.
     *
     * <p>The branch still exists on such a server, because taking it away would leave a player who types it
     * with the server's own red error and no reason. It answers instead, in our words.
     */
    private boolean refusedBecauseForced(Player player) {
        Optional<Locale> forced = settings.current().language().forcedLocale();
        if (forced.isEmpty()) {
            return false;
        }
        messages.send(player, Keys.LANGUAGE_FORCED, Text.placeholder("language", LanguageNames.of(forced.get())));
        return true;
    }

    /**
     * The player behind the sender.
     *
     * <p>{@code @PlayerOnly} has already refused a console sender before the method runs, so this cast cannot
     * fail. The check is here so that a future change which drops the annotation fails loudly rather than
     * throwing a {@code ClassCastException} at a player.
     */
    private Player player(Sender sender) {
        if (sender.bukkit() instanceof Player player) {
            return player;
        }
        throw new IllegalStateException(
                "a player-only command reached a " + sender.bukkit().getClass());
    }
}
