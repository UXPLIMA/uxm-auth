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
    VERSION("admin.version", "<tag:'TEMPLATE'> <body>uxmTemplate <value><version>"),
    RELOADED("admin.reloaded", "<tag:'TEMPLATE'> <body>The configuration is loaded again, in <value><ms><body> ms."),
    RELOAD_FAILED("admin.reload-failed", "<etag:'ERROR'> <body>The reload failed. The console holds the reason."),

    NO_PERMISSION("error.no-permission", "<etag:'ERROR'> <body>You do not have permission to do that."),
    PLAYER_ONLY("error.player-only", "<etag:'ERROR'> <body>Only a player can run this command."),
    INVALID_NAME(
            "error.invalid-name", "<etag:'ERROR'> <body>A name holds lower-case letters, digits and hyphens only."),

    EXAMPLE_CREATED("example.created", "<tag:'TEMPLATE'> <body><value><name><body> is created."),
    EXAMPLE_EXISTS("example.exists", "<etag:'ERROR'> <body>You already have <value><name><body>."),
    EXAMPLE_LIMIT("example.limit", "<etag:'ERROR'> <body>You have reached your limit of <value><limit><body>."),
    EXAMPLE_LIST_EMPTY("example.list-empty", "<tag:'TEMPLATE'> <body>You have nothing yet."),
    EXAMPLE_LIST("example.list", "<tag:'TEMPLATE'> <body>Yours: <value><names>");

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
