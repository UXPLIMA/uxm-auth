package com.uxplima.template.application.message;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every string that a player can see. A key carries its own English default, so the plugin can never ship a
 * message with no text, and an operator who deletes a line still gets something readable.
 *
 * <p>No user-facing text appears anywhere else. A literal in a command or a listener is a defect, and
 * {@code NoInlineTextTest} fails the build over one.
 */
public enum Keys implements MessageKey {
    PREFIX("prefix", "<gradient:#5eead4:#38bdf8><bold>uxm</bold></gradient> <dark_gray>|</dark_gray> "),

    VERSION("admin.version", "<gray>uxmTemplate <white><version></white>"),
    RELOADED("admin.reloaded", "<green>Configuration reloaded in <white><ms></white> ms."),
    RELOAD_FAILED("admin.reload-failed", "<red>The reload failed. Read the console for the reason."),
    NO_PERMISSION("error.no-permission", "<red>You do not have permission to do that."),
    PLAYER_ONLY("error.player-only", "<red>Only a player can run this command."),
    INVALID_NAME("error.invalid-name", "<red>A name uses lower-case letters, digits and a hyphen only."),

    EXAMPLE_CREATED("example.created", "<green>Created <white><name></white>."),
    EXAMPLE_EXISTS("example.exists", "<red>You already have <white><name></white>."),
    EXAMPLE_LIMIT("example.limit", "<red>You have reached your limit of <white><limit></white>."),
    EXAMPLE_LIST_EMPTY("example.list-empty", "<gray>You have nothing yet."),
    EXAMPLE_LIST("example.list", "<gray>Yours: <white><names></white>");

    private final String path;
    private final String defaultTemplate;

    Keys(String path, String defaultTemplate) {
        this.path = path;
        this.defaultTemplate = defaultTemplate;
    }

    @Override
    public String path() {
        return path;
    }

    @Override
    public String defaultTemplate() {
        return defaultTemplate;
    }
}
