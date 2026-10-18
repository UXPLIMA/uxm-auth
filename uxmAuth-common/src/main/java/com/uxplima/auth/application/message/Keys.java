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
    TIME_DAYS("time.days", "<n>d"),
    TIME_HOURS("time.hours", "<n>h"),
    TIME_MINUTES("time.minutes", "<n>m"),
    TIME_SECONDS("time.seconds", "<n>s"),
    TIME_SEPARATOR("time.separator", " "),
    KICK_BANNED(
            "kick.banned", "<body>Too many wrong passwords came from your address. Try again in <value><time><body>."),
    KICK_LOGGED_OUT("kick.logged-out", "<body>You have logged out. Join again to come back in."),
    KICK_TIMEOUT("kick.timeout", "<body>You took too long to log in. Join again to try once more."),
    KICK_CANCELLED("kick.cancelled", "<body>You left before logging in. Join again whenever you like."),
    KICK_STORAGE("kick.storage", "<body>This server cannot reach its accounts right now. Try again in a minute."),
    KICK_TOO_MANY_ATTEMPTS("kick.too-many-attempts", "<body>Too many wrong passwords. Join again to try once more."),
    KICK_NAME_TOO_SHORT(
            "kick.name-too-short",
            "<body>Your name is too short here. Use a name of at least <value><min><body> characters."),
    KICK_NAME_TOO_LONG(
            "kick.name-too-long",
            "<body>Your name is too long here. Use a name of at most <value><max><body> characters."),
    KICK_NAME_CHARACTERS(
            "kick.name-characters", "<body>Your name holds a character this server does not allow. Use another name."),
    KICK_NAME_BLOCKED("kick.name-blocked", "<body>Your name holds a word this server does not allow: <value><word>"),
    KICK_LOOKUP_FAILED(
            "kick.lookup-failed",
            "<body>This server cannot check right now whether your name belongs to a paid Minecraft account."
                    + " Try again in a minute."),
    KICK_PAID_NAME(
            "kick.paid-name",
            "<body>This name belongs to a paid Minecraft account, and your last try did not pass Mojang's check."
                    + " If the name is yours, join from the official launcher. If it is not, join under another name."),
    KICK_PAID_NAME_MOVED(
            "kick.paid-name-moved",
            "<body>This name now belongs to its paid owner. Your account here is now <value><name><body>:"
                    + " change your name to it and join again. Your password and everything you had are as they were."),
    KICK_PREMIUM_ADDRESS(
            "kick.premium-address",
            "<body>This address is for paid Minecraft accounts, and your last try did not pass Mojang's check."
                    + " Join through the server's other address to play with a password."),
    KICK_NAME_DISPUTED(
            "kick.name-disputed",
            "<body>Two players want this name, and staff will settle it. Join again once they have."),
    KICK_NAME_KEPT(
            "kick.name-kept",
            "<body>Another player took this name here first and keeps it. Change your name at Mojang to play here."),
    KICK_CLAIMED(
            "kick.claimed",
            "<body>Join again from the official Minecraft launcher. Your next connection from here proves this"
                    + " name is yours."),
    KICK_NETWORK_CLAIM(
            "kick.network-claim", "<body>This server could not confirm your login with the network. Join again."),
    KICK_NAME_CASE(
            "kick.name-case",
            "<body>This name is registered as <value><name><body>. Join again with exactly that spelling."),

    LOGIN_WRONG("login.wrong", "<etag:'ERROR'> <body>That password is wrong. Tries left: <value><left><body>."),
    LOGIN_NOT_REGISTERED(
            "login.not-registered",
            "<etag:'ERROR'> <body>You have no password here yet. Register with <value>/register<body>."),
    LOGIN_ALREADY("login.already", "<tag:'AUTH'> <body>You are already logged in."),
    CLAIM_UNAVAILABLE(
            "claim.unavailable",
            "<etag:'ERROR'> <body>This server cannot ask Mojang who owns a name, so there is nothing to claim here."),
    CLAIM_ALREADY_PREMIUM("claim.already-premium", "<tag:'AUTH'> <body>Your account has proved its name already."),
    CLAIM_NOT_THIS_RULE(
            "claim.not-this-rule",
            "<etag:'ERROR'> <body>This server proves paid names on its own, so there is nothing to claim."),
    PREMIUM_LOG_IN_FIRST(
            "premium.log-in-first",
            "<etag:'ERROR'> <body>Log in first, then use <value>/premium<body> to prove your name on your next"
                    + " connection."),
    NETWORK_LOG_IN_FIRST(
            "network.log-in-first",
            "<etag:'ERROR'> <body>Log in first. Until then you stay on this server, and the network's own commands"
                    + " wait."),
    LOGIN_HANDSHAKE_FAILED(
            "login.handshake-failed",
            "<tag:'AUTH'> <body>Your last connection was refused because this name belongs to a paid Minecraft"
                    + " account and Mojang did not confirm it was you. This time you came in with a password instead."
                    + " If the name is yours, use <value>/claim<body>."),
    LOGIN_RENAMED(
            "login.renamed",
            "<tag:'AUTH'> <body>The paid owner of <value><old><body> proved the name, so your account moved to"
                    + " <value><new><body> with everything it had. Join as <value><new><body> from now on."),

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

    SESSION_USAGE(
            "session.usage",
            "<body>Use <value>/session on<body> to skip the password from this address next time, or"
                    + " <value>/session off<body> to be asked at every join."),
    SESSION_ON(
            "session.on",
            "<good>From this address you skip the password for <value><minutes><good> minutes after each login."),
    SESSION_OFF("session.off", "<good>You will be asked your password at every join."),
    SESSION_LOG_IN_FIRST("session.log-in-first", "<etag:'ERROR'> <body>Log in first, then choose."),
    SESSION_NOT_CHOSEN(
            "session.not-chosen",
            "<etag:'ERROR'> <body>This server decides sessions for everyone, so there is nothing to choose."),
    LOGOUT_NOT_LOGGED_IN("logout.not-logged-in", "<etag:'ERROR'> <body>You are not logged in."),
    LOGOUT_SESSIONS_ENDED(
            "logout.sessions-ended",
            "<body>Your password was changed, so you were logged out. Log in with the new one."),

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
    SCREEN_REGISTER_REMEMBER("screen.register.remember", "Remember me on this address"),
    SCREEN_REGISTER_SUBMIT("screen.register.submit", "Register"),
    SCREEN_REGISTER_CANCEL("screen.register.cancel", "Leave"),
    SCREEN_LOGIN_TITLE("screen.login.title", "<value>Welcome back"),
    SCREEN_LOGIN_BODY("screen.login.body", "<body>Type your password to come in."),
    SCREEN_LOGIN_PASSWORD("screen.login.password", "Password"),
    SCREEN_LOGIN_REMEMBER("screen.login.remember", "Remember me on this address"),
    SCREEN_LOGIN_SUBMIT("screen.login.submit", "Log in"),
    SCREEN_LOGIN_CLAIM("screen.login.claim", "This name is mine"),
    SCREEN_LOGIN_CANCEL("screen.login.cancel", "Leave"),
    SCREEN_PIN_TITLE("screen.pin.title", "<value>Your code"),
    SCREEN_PIN_BODY("screen.pin.body", "<body>Press the digits of your code, then enter."),
    SCREEN_PIN_ERASE("screen.pin.erase", "Erase"),
    SCREEN_PIN_ENTER("screen.pin.enter", "Enter"),
    SCREEN_PIN_REMEMBER("screen.pin.remember", "Remember me on this address"),
    SCREEN_PIN_CANCEL("screen.pin.cancel", "Leave"),
    SCREEN_PIN_ENTERED("screen.pin.entered", "<body>Code: <value><code>"),
    SCREEN_PIN_REPEAT("screen.pin.repeat", "<body>Press the same code once more to confirm it."),
    SCREEN_PIN_MASK("screen.pin.mask", "●"),
    RULES_LINE_1("rules.line-1", "<body>Be kind to other players."),
    RULES_LINE_2("rules.line-2", "<body>No cheating and no griefing."),
    RULES_ACCEPTED_BY_REGISTERING(
            "rules.accepted-by-registering",
            "<body>Registering with <value>/register<body> means you accept these rules.");
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
