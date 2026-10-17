package com.uxplima.auth.application.message;

import com.uxplima.uxmlib.text.message.MessageKey;

/**
 * Every string that a player can see. A key carries its own English default, so the plugin can never ship a
 * message with no text, and an operator who deletes a line still gets something readable.
 *
 * <p>No user-facing text appears anywhere else. A literal in a command or a listener is a defect, and
 * {@code NoInlineTextTest} fails the build over one.
 */
public enum Keys implements MessageKey {
    USAGE("admin.usage", "<tag:'AUTH'> <body>Add <value>help<body> after this command to see everything it does."),
    VERSION("admin.version", "<tag:'AUTH'> <body>uxmAuth <value><version>"),
    RELOADED("admin.reloaded", "<tag:'AUTH'> <body>The configuration is loaded again, in <value><ms><body> ms."),
    RELOAD_FAILED("admin.reload-failed", "<etag:'ERROR'> <body>The reload failed. The console holds the reason."),

    LANGUAGE_CURRENT("language.current", "<tag:'AUTH'> <body>You read this in <value><language><body>."),
    LANGUAGE_CHOICES("language.choices", "<tag:'AUTH'> <body>You may read it in <value><languages><body>."),
    LANGUAGE_CHANGED("language.changed", "<tag:'AUTH'> <body>You now read this in <value><language><body>."),
    LANGUAGE_ALREADY("language.already", "<tag:'AUTH'> <body>You already read this in <value><language><body>."),
    LANGUAGE_RESET("language.reset", "<tag:'AUTH'> <body>You no longer have a language of your own."),
    LANGUAGE_SEPARATOR("language.separator", "<dim>, "),
    LANGUAGE_UNKNOWN(
            "language.unknown",
            "<etag:'ERROR'> <body>This server has no <value><input><body>. It has <value><languages><body>."),
    LANGUAGE_FORCED(
            "language.forced",
            "<etag:'ERROR'> <body>This server reads one language, and it is <value><language><body>."),
    LANGUAGE_SET_PLAYER("language.set-player", "<tag:'AUTH'> <value><player><body> now reads <value><language><body>."),
    LANGUAGE_SET_PLAYER_AWAY(
            "language.set-player-away",
            "<tag:'AUTH'> <value><player><body> is away and reads <value><language><body> from their next join."),
    LANGUAGE_RESET_PLAYER(
            "language.reset-player", "<tag:'AUTH'> <value><player><body> no longer has a language of their own."),
    LANGUAGE_RESET_PLAYER_AWAY(
            "language.reset-player-away",
            "<tag:'AUTH'> <value><player><body> is away, and their language is cleared at their next join."),
    LANGUAGE_FORCED_ANYWAY(
            "language.forced-anyway",
            "<tag:'AUTH'> <body>It is kept, and everyone reads <value><language><body> while that is forced."),
    LANGUAGE_UNKNOWN_PLAYER(
            "language.unknown-player", "<etag:'ERROR'> <body>Nobody called <value><input><body> has played here."),

    COMMAND_NO_PERMISSION("command.no-permission", "<etag:'ERROR'> <body>You do not have permission to do that."),

    COMMAND_PLAYER_ONLY("command.player-only", "<etag:'ERROR'> <body>Only a player can run this command."),
    COMMAND_INVALID_VALUE(
            "command.invalid-value",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>."),
    COMMAND_INVALID_VALUE_WHY(
            "command.invalid-value-why",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>: <subtext><reason>"),
    COMMAND_NOT_ONE_OF(
            "command.not-one-of",
            "<etag:'ERROR'> <body><value><input><body> is not a valid <value><argument><body>. Try one of <value><allowed><body>."),
    COMMAND_INVALID_ARGUMENT("command.invalid-argument", "<etag:'ERROR'> <body><detail>"),
    COMMAND_BAD_ARGUMENT("command.bad-argument", "<etag:'ERROR'> <body>That argument is not valid."),
    COMMAND_INTERNAL_ERROR(
            "command.internal-error",
            "<etag:'ERROR'> <body>Something went wrong while running this command. The console holds the reason."),
    COMMAND_ON_COOLDOWN(
            "command.on-cooldown", "<etag:'ERROR'> <body>Wait <value><time><body> before you use this again."),
    COMMAND_HELP_HEADER(
            "command.help-header", "<h:'Help'> <dim>/<value><command> <dim>(<value><page><dim>/<value><pages><dim>)"),
    COMMAND_HELP_COMMAND("command.help-command", "<value><command>"),
    COMMAND_HELP_SEPARATOR("command.help-separator", "<dim> : "),
    COMMAND_HELP_DESCRIPTION("command.help-description", "<subtext><description>"),
    COMMAND_HELP_FILL_HINT("command.help-fill-hint", "<subtext>Click to write this command."),
    COMMAND_HELP_PAGE_HINT("command.help-page-hint", "<subtext>Page <value><page>"),

    // The doctor: one line per thing that can be wrong, and a closing line that says whether anything
    // needs an operator. Every plugin of ours answers this in the same six lines.
    DOCTOR_HEADER("admin.doctor.header", "<h:'How this plugin is'>"),
    DOCTOR_OK("admin.doctor.ok", "<good>ok<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_WARN("admin.doctor.warn", "<warn>warn<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_FAIL("admin.doctor.fail", "<bad>fail<body>  <value><check><body>: <subtext><said>"),
    DOCTOR_WELL("admin.doctor.well", "<tag:'AUTH'> <body>Everything answered."),
    DOCTOR_UNWELL(
            "admin.doctor.unwell",
            "<etag:'ERROR'> <body>Something is wrong above. The console carries the whole of it."),

    JOIN_REGISTER_TITLE("join.register-title", "<value>Welcome"),
    JOIN_REGISTER_SUBTITLE("join.register-subtitle", "<body>Choose a password with <value>/register"),
    JOIN_LOGIN_TITLE("join.login-title", "<value>Welcome back"),
    JOIN_LOGIN_SUBTITLE("join.login-subtitle", "<body>Log in with <value>/login"),
    REMINDER_REGISTER("reminder.register", "<body>Register with <value>/register <password> <password>"),
    REMINDER_LOGIN("reminder.login", "<body>Log in with <value>/login <password>"),
    WELCOME_REGISTERED("welcome.registered", "<good>Your account is ready"),
    WELCOME_LOGGED_IN("welcome.logged-in", "<good>Logged in"),

    KICK_ALREADY_ONLINE(
            "kick.already-online",
            "<body>Someone is already connected under this name. If that was you, wait a moment and join again."),
    KICK_TIMEOUT("kick.timeout", "<body>You took too long to log in. Join again to try once more."),
    KICK_CANCELLED("kick.cancelled", "<body>You left before logging in. Join again whenever you like."),
    KICK_STORAGE("kick.storage", "<body>This server cannot reach its accounts right now. Try again in a minute."),
    KICK_TOO_MANY_ATTEMPTS("kick.too-many-attempts", "<body>Too many wrong passwords. Join again to try once more."),

    LOGIN_WRONG("login.wrong", "<etag:'ERROR'> <body>That password is wrong. Tries left: <value><left><body>."),
    LOGIN_NOT_REGISTERED(
            "login.not-registered",
            "<etag:'ERROR'> <body>You have no password here yet. Register with <value>/register<body>."),
    LOGIN_ALREADY("login.already", "<tag:'AUTH'> <body>You are already logged in."),

    REGISTER_MISMATCH("register.mismatch", "<etag:'ERROR'> <body>The two passwords are not the same."),
    REGISTER_CONFIRM_NEEDED(
            "register.confirm-needed",
            "<etag:'ERROR'> <body>Write the password twice: <value>/register <password> <password>"),
    REGISTER_NAME_TAKEN(
            "register.name-taken", "<etag:'ERROR'> <body>The name <value><name><body> already has an account."),
    REGISTER_RULES_NEEDED("register.rules-needed", "<etag:'ERROR'> <body>Tick the box to accept the rules first."),
    REGISTER_ALREADY(
            "register.already", "<etag:'ERROR'> <body>You already have an account. Log in with <value>/login<body>."),

    CHANGE_WRONG_OLD("change.wrong-old", "<etag:'ERROR'> <body>The old password is wrong."),
    CHANGE_NOT_LOGGED_IN("change.not-logged-in", "<etag:'ERROR'> <body>Log in first."),
    CHANGE_NEEDS_BOTH("change.needs-both", "<etag:'ERROR'> <body>Write the old password, then the new one."),

    LOGOUT_NOT_LOGGED_IN("logout.not-logged-in", "<etag:'ERROR'> <body>You are not logged in."),

    PASSWORD_TOO_SHORT(
            "password.too-short", "<etag:'ERROR'> <body>A password needs at least <value><min><body> characters."),
    PASSWORD_TOO_LONG(
            "password.too-long", "<etag:'ERROR'> <body>A password may have at most <value><max><body> characters."),
    PASSWORD_TOO_LONG_FOR_THE_HASH(
            "password.too-long-for-the-hash",
            "<etag:'ERROR'> <body>That password is too long to keep whole. Use fewer letters outside the English alphabet."),
    PASSWORD_BAD_CHARACTERS(
            "password.bad-characters",
            "<etag:'ERROR'> <body>That password holds a character this server does not allow."),
    PASSWORD_MISSING("password.missing", "<etag:'ERROR'> <body>A password here needs <value><classes><body>."),
    PASSWORD_CLASS_LOWER("password.class-lower", "a lower-case letter"),
    PASSWORD_CLASS_UPPER("password.class-upper", "a capital letter"),
    PASSWORD_CLASS_DIGIT("password.class-digit", "a digit"),
    PASSWORD_CLASS_SYMBOL("password.class-symbol", "a symbol"),
    PASSWORD_CLASS_SEPARATOR("password.class-separator", ", "),

    BUSY("busy", "<etag:'ERROR'> <body>The server is busy. Try again in a moment."),

    SCREEN_REGISTER_TITLE("screen.register.title", "<value>Welcome"),
    SCREEN_REGISTER_BODY(
            "screen.register.body", "<body>Choose a password for this name. You will type it each time you join."),
    SCREEN_REGISTER_PASSWORD("screen.register.password", "Password"),
    SCREEN_REGISTER_CONFIRM("screen.register.confirm", "The same password again"),
    SCREEN_REGISTER_ACCEPT("screen.register.accept", "I have read the rules and accept them"),
    SCREEN_REGISTER_SUBMIT("screen.register.submit", "Register"),
    SCREEN_REGISTER_CANCEL("screen.register.cancel", "Leave"),
    SCREEN_LOGIN_TITLE("screen.login.title", "<value>Welcome back"),
    SCREEN_LOGIN_BODY("screen.login.body", "<body>Type your password to come in."),
    SCREEN_LOGIN_PASSWORD("screen.login.password", "Password"),
    SCREEN_LOGIN_SUBMIT("screen.login.submit", "Log in"),
    SCREEN_LOGIN_CANCEL("screen.login.cancel", "Leave"),
    SCREEN_PIN_TITLE("screen.pin.title", "<value>Your code"),
    SCREEN_PIN_BODY("screen.pin.body", "<body>Press the digits of your code, then enter."),
    SCREEN_PIN_ERASE("screen.pin.erase", "Erase"),
    SCREEN_PIN_ENTER("screen.pin.enter", "Enter"),
    SCREEN_PIN_CANCEL("screen.pin.cancel", "Leave"),
    RULES_LINE_1("rules.line-1", "<body>Be kind to other players."),
    RULES_LINE_2("rules.line-2", "<body>No cheating and no griefing.");
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
