package com.uxplima.auth.paper.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

import com.uxplima.auth.adapter.outbound.PluginSettings;
import com.uxplima.auth.application.message.Keys;
import com.uxplima.auth.application.perf.Timings;
import com.uxplima.auth.application.staff.ResolveConflict;
import com.uxplima.auth.paper.adapter.inbound.staff.DatabaseActions;
import com.uxplima.auth.paper.adapter.inbound.staff.PerfView;
import com.uxplima.auth.paper.adapter.inbound.staff.Staff;
import com.uxplima.auth.paper.adapter.inbound.staff.StaffActions;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.PlayerNames;
import com.uxplima.uxmlib.command.annotation.SuggestUsing;
import com.uxplima.uxmlib.command.annotation.annotations.Arg;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.PlayerOnly;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.health.HealthCheck;
import com.uxplima.uxmlib.health.HealthReport;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.language.LanguageChoices;
import com.uxplima.uxmlib.text.language.LanguageNames;
import com.uxplima.uxmlib.text.message.Messages;

/**
 * The operator surface: reload the configuration, report what the plugin is running, and the language branch
 * every plugin of ours carries.
 */
@FromConfig(value = AdminCommand.KEY, fallbackName = "uxmauth", description = "Administer the plugin")
public final class AdminCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "uxmauth";

    private final PluginSettings settings;
    private final Messages messages;
    private final String version;

    private final Scheduler scheduler;
    private final Supplier<List<HealthCheck>> health;
    private final LanguageChoices choices;
    private final LanguageOfAnotherPlayer another;
    private final Staff staff;
    private final StaffActions actions;
    private final PerfView perf;
    private final DatabaseActions databases;

    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health,
            LanguageChoices choices) {
        this(settings, messages, version, scheduler, health, choices, Staff.NONE);
    }

    /** @param staff what staff read of an account */
    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health,
            LanguageChoices choices,
            Staff staff) {
        this(settings, messages, version, scheduler, health, choices, staff, StaffActions.NONE);
    }

    /**
     * @param staff what staff read of an account
     * @param actions what staff do to an account
     */
    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health,
            LanguageChoices choices,
            Staff staff,
            StaffActions actions) {
        this(
                settings,
                messages,
                version,
                scheduler,
                health,
                choices,
                staff,
                actions,
                new PerfView(new Timings(System::nanoTime), messages));
    }

    /**
     * @param staff what staff read of an account
     * @param actions what staff do to an account
     * @param perf the time spent on each kind of work
     */
    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health,
            LanguageChoices choices,
            Staff staff,
            StaffActions actions,
            PerfView perf) {
        this(settings, messages, version, scheduler, health, choices, staff, actions, perf, DatabaseActions.NONE);
    }

    /**
     * @param staff what staff read of an account
     * @param actions what staff do to an account
     * @param perf the time spent on each kind of work
     * @param databases what the console does to the database
     */
    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health,
            LanguageChoices choices,
            Staff staff,
            StaffActions actions,
            PerfView perf,
            DatabaseActions databases) {
        this.databases = Objects.requireNonNull(databases, "databases");
        this.perf = Objects.requireNonNull(perf, "perf");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.health = Objects.requireNonNull(health, "health");
        this.choices = Objects.requireNonNull(choices, "choices");
        this.staff = Objects.requireNonNull(staff, "staff");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.another = new LanguageOfAnotherPlayer(
                messages, choices, () -> settings.current().language().forcedLocale());
    }

    /**
     * The bare command answers in our own words.
     *
     * <p>A root with no executor is answered by the server itself, in its own English and its own red. Every
     * line a person reads is ours, so the root says what the command is and where the rest of it is written.
     */
    @Subcommand("")
    @Permission("uxmauth.admin.version")
    public void root(Sender sender) {
        messages.send(sender.bukkit(), Keys.USAGE);
    }

    @FromConfig(value = "reload", fallbackName = "reload", description = "Read the configuration and the content again")
    @Permission("uxmauth.admin.reload")
    public void reload(Sender sender) {
        long startedAt = System.nanoTime();
        try {
            settings.reload();
        } catch (RuntimeException reloadFailed) {
            // The previous snapshot stays in place, so the server keeps running on the last good
            // configuration. The console carries the reason; the operator only needs to know it failed.
            messages.send(sender.bukkit(), Keys.RELOAD_FAILED);
            throw reloadFailed;
        }
        long millis = (System.nanoTime() - startedAt) / 1_000_000L;
        messages.send(sender.bukkit(), Keys.RELOADED, Text.placeholder("ms", String.valueOf(millis)));
    }

    @FromConfig(value = "version", fallbackName = "version", description = "Read the version this server runs")
    @Permission("uxmauth.admin.version")
    public void version(Sender sender) {
        messages.send(sender.bukkit(), Keys.VERSION, Text.placeholder("version", version));
    }
    /**
     * Say how this plugin is, in one line per thing that can be wrong.
     *
     * <p>Everything this plugin could not read at boot went to the console and nowhere else, so an operator
     * who was not watching it had no way to ask again. The checks are read only and one of them opens a
     * connection, so the whole run is on the lane and only the answer comes back.
     */
    @FromConfig(
            value = "doctor",
            fallbackName = "doctor",
            description = "Say how this plugin is, in one line per thing that can be wrong")
    @Permission("uxmauth.admin.doctor")
    public void doctor(Sender sender) {
        messages.send(sender.bukkit(), Keys.DOCTOR_HEADER);
        scheduler.async(() -> say(sender, HealthReport.run(health.get())));
    }

    /** Lets a waiting player in past the login, on whichever server they wait. */
    @FromConfig(value = "forcelogin", fallbackName = "forcelogin", description = "Let a waiting player in")
    @Permission("uxmauth.admin.forcelogin")
    public void forceLogin(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = actions.forceLogin(sender.bukkit(), player);
    }

    /** Lifts the ban on an address and forgets its wrong passwords. */
    @FromConfig(value = "unban", fallbackName = "unban", description = "Lift the ban on an address")
    @Permission("uxmauth.admin.unban")
    public void unban(Sender sender, @Arg("address") String address) {
        CompletableFuture<Void> unused = actions.unban(sender.bukkit(), address);
    }

    /** Removes an account and everything kept under it; its player is sent away wherever they are. */
    @FromConfig(value = "unregister", fallbackName = "unregister", description = "Remove an account")
    @Permission("uxmauth.admin.unregister")
    public void unregister(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = actions.unregister(sender.bukkit(), player);
    }

    /** Sets an account's password and ends its sessions. The password is kept out of the console. */
    @FromConfig(value = "setpassword", fallbackName = "setpassword", description = "Set an account's password")
    @Permission("uxmauth.admin.setpassword")
    public void setPassword(
            Sender sender,
            @Arg("player") @SuggestUsing(PlayerNames.KEY) String player,
            @Arg("password") String password) {
        CompletableFuture<Void> unused = actions.setPassword(sender.bukkit(), player, password);
    }

    /** Makes a password account under a name. The password is kept out of the console. */
    @FromConfig(value = "forceregister", fallbackName = "forceregister", description = "Make an account")
    @Permission("uxmauth.admin.forceregister")
    public void forceRegister(
            Sender sender,
            @Arg("player") @SuggestUsing(PlayerNames.KEY) String player,
            @Arg("password") String password) {
        CompletableFuture<Void> unused = actions.forceRegister(sender.bukkit(), player, password);
    }

    /** Turns off every second factor of an account, for a player who lost the phone and the backup codes. */
    @FromConfig(value = "disable2fa", fallbackName = "disable2fa", description = "Turn off an account's second factor")
    @Permission("uxmauth.admin.disable2fa")
    public void disableFactor(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = actions.disableFactor(sender.bukkit(), player);
    }

    /** Turns a cracked account premium when Mojang names a paid account under its name. */
    @FromConfig(value = "markpremium", fallbackName = "markpremium", description = "Turn an account premium")
    @Permission("uxmauth.admin.markpremium")
    public void markPremium(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = actions.markPremium(sender.bukkit(), player);
    }

    /** Turns a premium account cracked; it logs in with its password from then on. */
    @FromConfig(value = "markcracked", fallbackName = "markcracked", description = "Turn an account cracked")
    @Permission("uxmauth.admin.markcracked")
    public void markCracked(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = actions.markCracked(sender.bukkit(), player);
    }

    /** Settles a name a cracked holder and a paid owner both want, for one of the two: premium or cracked. */
    @FromConfig(value = "resolve", fallbackName = "resolve", description = "Settle a name two players want")
    @Permission("uxmauth.admin.resolve")
    public void resolve(
            Sender sender,
            @Arg("player") @SuggestUsing(PlayerNames.KEY) String player,
            @Arg("side") ResolveConflict.Side side) {
        CompletableFuture<Void> unused = actions.resolve(sender.bukkit(), player, side);
    }

    /** Everything staff read of one account: its dates, logins, factors, address and the other accounts. */
    @FromConfig(value = "info", fallbackName = "info", description = "Read what is known of an account")
    @Permission("uxmauth.admin.info")
    public void info(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = staff.info(sender.bukkit(), player);
    }

    /** The latest logins of one account, with the time, the address and the way in of each. */
    @FromConfig(value = "history", fallbackName = "history", description = "Read the latest logins of an account")
    @Permission("uxmauth.admin.history")
    public void history(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String player) {
        CompletableFuture<Void> unused = staff.history(sender.bukkit(), player);
    }

    /** Copies every account to the database storage.move-to names. The console only, checked inside. */
    @FromConfig(
            value = "database-move",
            fallbackName = "database move",
            description = "Move the accounts to another database")
    @Permission("uxmauth.admin.database")
    public void moveDatabase(Sender sender) {
        CompletableFuture<Void> unused = databases.move(sender.bukkit());
    }

    /** The time spent hashing, querying the database and asking Mojang, over the last minute and since start. */
    @FromConfig(value = "perf", fallbackName = "perf", description = "Read the time spent on each kind of work")
    @Permission("uxmauth.admin.perf")
    public void perf(Sender sender) {
        perf.show(sender.bukkit());
    }

    /** One line per check, and a closing line that says whether anything needs an operator. */
    private void say(Sender sender, HealthReport report) {
        for (HealthReport.Entry entry : report.entries()) {
            messages.send(
                    sender.bukkit(),
                    switch (entry.result().status()) {
                        case OK -> Keys.DOCTOR_OK;
                        case WARN -> Keys.DOCTOR_WARN;
                        case FAIL -> Keys.DOCTOR_FAIL;
                    },
                    Text.placeholder("check", entry.name()),
                    Text.placeholder("said", entry.result().message()));
        }
        messages.send(sender.bukkit(), report.hasFailure() ? Keys.DOCTOR_UNWELL : Keys.DOCTOR_WELL);
    }

    /**
     * Say what language this player reads, and what else this server has.
     *
     * <p>The branch sits under this plugin's own root and not under a bare {@code /lang}, because sixteen
     * plugins that each registered {@code /lang} would collide and the last one enabled would win. A server
     * that runs uxmLang gets one command for the whole network as well, and this branch still answers.
     */
    @FromConfig(
            value = "lang",
            fallbackName = "lang",
            description = "Say what language this player reads, and what else this server has")
    @Permission("uxmauth.language")
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
    @FromConfig(
            value = "lang-set",
            fallbackName = "lang set",
            description = "Read this plugin in one language, whatever the client is set to")
    @Permission("uxmauth.language")
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
    @FromConfig(
            value = "lang-reset",
            fallbackName = "lang reset",
            description = "Follow the client again, as a player who never chose anything does")
    @Permission("uxmauth.language")
    @PlayerOnly
    public void resetLanguage(Sender sender) {
        Player player = player(sender);
        if (refusedBecauseForced(player)) {
            return;
        }
        choices.reset(player.getUniqueId());
        messages.send(player, Keys.LANGUAGE_RESET);
    }

    /** Set the language another player reads, for an operator. A player who is away reads it when they join. */
    @FromConfig(
            value = "lang-set-player",
            fallbackName = "lang set-player",
            description = "Set the language another player reads")
    @Permission("uxmauth.admin.language")
    public void setLanguageOf(
            Sender sender,
            @Arg("player") @SuggestUsing(PlayerNames.KEY) String name,
            @Arg("language") @SuggestUsing(LanguageSuggestions.KEY) String language) {
        another.set(sender.bukkit(), name, language);
    }

    /** Clear the language another player chose, for an operator. A player who is away has it cleared on join. */
    @FromConfig(
            value = "lang-reset-player",
            fallbackName = "lang reset-player",
            description = "Clear the language another player chose")
    @Permission("uxmauth.admin.language")
    public void resetLanguageOf(Sender sender, @Arg("player") @SuggestUsing(PlayerNames.KEY) String name) {
        another.reset(sender.bukkit(), name);
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
