package com.uxplima.template.adapter.inbound.command;

import java.util.Objects;

import com.uxplima.template.adapter.outbound.PluginSettings;
import com.uxplima.template.application.message.Keys;
import com.uxplima.uxmlib.command.Sender;
import com.uxplima.uxmlib.command.annotation.annotations.Permission;
import com.uxplima.uxmlib.command.annotation.annotations.Subcommand;
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

    public AdminCommand(PluginSettings settings, Messages messages, String version) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.version = Objects.requireNonNull(version, "version");
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

    @Subcommand("reload")
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

    @Subcommand("version")
    @Permission("uxmtemplate.admin.version")
    public void version(Sender sender) {
        messages.send(sender.bukkit(), Keys.VERSION, Text.placeholder("version", version));
    }
}
