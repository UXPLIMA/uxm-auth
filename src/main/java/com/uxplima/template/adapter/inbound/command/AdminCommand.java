package com.uxplima.template.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.FromConfig;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
import com.uxplima.uxmlib.health.HealthCheck;
import com.uxplima.uxmlib.health.HealthReport;
import com.uxplima.uxmlib.scheduler.Scheduler;
import com.uxplima.uxmlib.text.Text;
import com.uxplima.uxmlib.text.message.Messages;

/** The operator surface: reload the configuration, and report what the plugin is running. */
@FromConfig(value = AdminCommand.KEY, fallbackName = "uxmtemplate", description = "Administer the plugin")
public final class AdminCommand {

    /** The key this command is named under in {@code commands.conf}. */
    public static final String KEY = "uxmtemplate";

    private final PluginSettings settings;
    private final Messages messages;
    private final String version;

    private final Scheduler scheduler;
    private final Supplier<List<HealthCheck>> health;

    public AdminCommand(
            PluginSettings settings,
            Messages messages,
            String version,
            Scheduler scheduler,
            Supplier<List<HealthCheck>> health) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.health = Objects.requireNonNull(health, "health");
    }

    /**
     * The bare command answers in our own words.
     *
     * <p>A root with no executor is answered by the server itself, in its own English and its own red. Every
     * line a person reads is ours, so the root says what the command is and where the rest of it is written.
     */
    @Subcommand("")
    @Permission("uxmtemplate.admin.version")
    public void root(Sender sender) {
        messages.send(sender.bukkit(), Keys.USAGE);
    }

    @FromConfig(value = "reload", fallbackName = "reload", description = "Read the configuration and the content again")
    @Permission("uxmtemplate.admin.reload")
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
    @Permission("uxmtemplate.admin.version")
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
    @Permission("uxmtemplate.admin.doctor")
    public void doctor(Sender sender) {
        messages.send(sender.bukkit(), Keys.DOCTOR_HEADER);
        scheduler.async(() -> say(sender, HealthReport.run(health.get())));
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
}
