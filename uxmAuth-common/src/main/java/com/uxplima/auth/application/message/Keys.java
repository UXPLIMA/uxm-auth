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
    COMMAND_STORAGE(
            "command.storage",
            "<etag:'ERROR'> <body>This server cannot reach its accounts right now. Try again in a minute."),
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
    JOIN_CODE_TITLE("join.code-title", "<value>One more step"),
    JOIN_CODE_SUBTITLE("join.code-subtitle", "<body>Type your login code with <value>/2fa"),
    REMINDER_REGISTER("reminder.register", "<body>Register with <value>/register <password> <password>"),
    REMINDER_LOGIN("reminder.login", "<body>Log in with <value>/login <password>"),
    REMINDER_CODE("reminder.code", "<body>Type your login code with <value>/2fa <code>"),
    CHAT_REGISTER_PASSWORD(
            "chat.register-password", "<body>Type a password for this name in chat. Nobody else reads it."),
    CHAT_REGISTER_REPEAT("chat.register-repeat", "<body>Type the same password again."),
    CHAT_LOGIN_PASSWORD("chat.login-password", "<body>Type your password in chat to log in. Nobody else reads it."),
    CHAT_CODE("chat.code", "<body>Type your login code in chat: the one your app shows, or the one mailed to you."),
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
    REGISTER_TOO_MANY_ACCOUNTS(
            "register.too-many-accounts",
            "<etag:'ERROR'> <body>Too many accounts were made from your address lately. Try again later."),
    KICK_COUNTRY("kick.country", "<body>This server does not take players from where you connect."),
    KICK_TOO_MANY_CONNECTIONS(
            "kick.too-many-connections",
            "<body>Too many connections came from your address. Try again in <value><time><body>."),
    KICK_BANNED(
            "kick.banned", "<body>Too many wrong passwords came from your address. Try again in <value><time><body>."),
    KICK_LOGGED_OUT("kick.logged-out", "<body>You have logged out. Join again to come back in."),
    KICK_TIMEOUT("kick.timeout", "<body>You took too long to log in. Join again to try once more."),
    KICK_TIMEOUT_REGISTER("kick.timeout-register", "<body>You took too long to register. Join again to try once more."),
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
    LOGIN_USAGE("login.usage", "<tag:'AUTH'> <body>Type your password after the command: <value>/login <password>"),
    LOGIN_ALREADY("login.already", "<tag:'AUTH'> <body>You are already logged in."),
    LOGIN_REFUSED("login.refused", "<tag:'AUTH'> <body>Your login was refused."),
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
    REGISTER_USAGE(
            "register.usage",
            "<tag:'AUTH'> <body>Type a password after the command, twice: <value>/register <password> <password>"),
    REGISTER_USAGE_ONCE(
            "register.usage-once", "<tag:'AUTH'> <body>Type a password after the command: <value>/register <password>"),
    REGISTER_CONFIRM_NEEDED(
            "register.confirm-needed",
            "<etag:'ERROR'> <body>Write the password twice: <value>/register <password> <password>"),
    REGISTER_NAME_TAKEN(
            "register.name-taken", "<etag:'ERROR'> <body>The name <value><name><body> already has an account."),
    REGISTER_RULES_NEEDED("register.rules-needed", "<etag:'ERROR'> <body>Tick the box to accept the rules first."),
    REGISTER_ALREADY(
            "register.already", "<etag:'ERROR'> <body>You already have an account. Log in with <value>/login<body>."),
    REGISTER_REFUSED("register.refused", "<tag:'AUTH'> <body>Your registration was refused."),

    START_FAILED_KICK(
            "kick.start-failed",
            "This server's login could not start, so nobody can join until its staff fix it. Try again later."),
    CHANGE_DONE("change.done", "<good>Your password is changed. Every remembered session of it has ended."),
    CHANGE_WRONG_OLD("change.wrong-old", "<etag:'ERROR'> <body>The old password is wrong."),
    CHANGE_NOT_LOGGED_IN("change.not-logged-in", "<etag:'ERROR'> <body>Log in first."),
    CHANGE_USAGE(
            "change.usage",
            "<tag:'AUTH'> <body>Type the old password and then the new one: <value>/changepassword <old> <new>"),
    CHANGE_USAGE_ONCE(
            "change.usage-once",
            "<tag:'AUTH'> <body>Type the new password after the command: <value>/changepassword <new>"),
    CHANGE_NEEDS_BOTH("change.needs-both", "<etag:'ERROR'> <body>Write the old password, then the new one."),

    SESSION_USAGE(
            "session.usage",
            "<body>Use <value>/session on<body> to skip the password from this address next time, or"
                    + " <value>/session off<body> to be asked at every join."),
    SESSION_ON(
            "session.on",
            "<good>From this address you skip the password for <value><minutes><good> minutes after each login."),
    SESSION_OFF("session.off", "<good>You will be asked your password at every join."),
    FACTOR_USAGE(
            "factor.usage",
            "<body>Use <value>/2fa setup<body> to ask a code from an authenticator app after your password,"
                    + " <value>/2fa email on<body> to get it by mail instead, <value>/2fa backup <code><body> for new"
                    + " backup codes, or <value>/2fa off <code><body> to stop."),
    FACTOR_SECRET(
            "factor.secret",
            "<body>Add an account in your authenticator app with this key: <value><secret><body>. Click it to copy."),
    FACTOR_LINK("factor.link", "<body>Or open this link on the phone: <value><link><body>. Click it to copy."),
    FACTOR_MAP("factor.map", "<body>Or scan the QR code on the map in your hand."),
    FACTOR_NO_ROOM(
            "factor.no-room",
            "<body>Empty a slot of your hotbar and run <value>/2fa setup<body> again to get the QR code on a map."),
    FACTOR_CONFIRM(
            "factor.confirm",
            "<body>Then type <value>/2fa confirm <code><body> with the code the app shows. This waits"
                    + " <value><minutes><body> minutes."),
    FACTOR_ON("factor.on", "<good>From now on your account asks a code from your app after the password."),
    FACTOR_OFF("factor.off", "<good>Your account no longer asks a code from an app."),
    FACTOR_WRONG("factor.wrong", "<etag:'ERROR'> <body>That is neither the code your app shows now nor a backup code."),
    FACTOR_BACKUP_CODES(
            "factor.backup-codes",
            "<body>Your backup codes, for a lost phone: <value><codes><body>. Each works once in place of the app's"
                    + " code. Click them to copy and keep them safe, they are not shown again."),
    FACTOR_NOTHING_STARTED(
            "factor.nothing-started",
            "<etag:'ERROR'> <body>No setup is waiting. Start one with <value>/2fa setup<body>."),
    FACTOR_ALREADY_ON(
            "factor.already-on",
            "<etag:'ERROR'> <body>Your account asks a code from an app already. Turn it off first with"
                    + " <value>/2fa off <code><body>."),
    FACTOR_NOT_ON("factor.not-on", "<etag:'ERROR'> <body>Your account asks no code from an app."),
    FACTOR_MAIL_ON(
            "factor.mail-on",
            "<good>From now on a login code is mailed to <value><address><good> after your password."),
    FACTOR_MAIL_OFF("factor.mail-off", "<good>No more login codes are mailed to you."),
    FACTOR_MAIL_ALREADY_ON("factor.mail-already-on", "<etag:'ERROR'> <body>Login codes are mailed to you already."),
    FACTOR_MAIL_NOT_ON("factor.mail-not-on", "<etag:'ERROR'> <body>No login codes are mailed to you."),
    FACTOR_MAIL_NO_ADDRESS(
            "factor.mail-no-address",
            "<etag:'ERROR'> <body>Link an address first with <value>/email add <address><body>."),
    FACTOR_REQUIRED(
            "factor.required",
            "<etag:'ERROR'> <body>Your account must ask a code from an authenticator app before you can play. Type"
                    + " <value>/2fa setup<body> to set it up."),
    FACTOR_LOG_IN_FIRST("factor.log-in-first", "<etag:'ERROR'> <body>Log in first."),
    FACTOR_CODE_NEEDED(
            "factor.code-needed",
            "<body>Now type the code from your authenticator app or the one mailed to you, or a backup code, with"
                    + " <value>/2fa <code><body>."),
    FACTOR_LOGIN_WRONG(
            "factor.login-wrong",
            "<etag:'ERROR'> <body>That is not the app's code now, the mailed code or a backup code. Tries left:"
                    + " <value><left><body>."),
    FACTOR_NOTHING_ASKED("factor.nothing-asked", "<etag:'ERROR'> <body>No code is asked of you now."),
    EMAIL_USAGE(
            "email.usage",
            "<body>Use <value>/email add <address><body> to link an address a forgotten password is"
                    + " sent to, <value>/email confirm <code><body> with the code it gets, or <value>/email"
                    + " remove<body> to take it away."),
    EMAIL_LINKED("email.linked", "<body>Linked now: <value><address><body>."),
    EMAIL_SENT(
            "email.sent",
            "<good>A code went to <value><address><good>. Type <value>/email confirm <code><good>"
                    + " within <value><minutes><good> minutes."),
    EMAIL_COOLDOWN(
            "email.cooldown",
            "<etag:'ERROR'> <body>A code went out a moment ago. Ask again in <value><seconds><body> seconds."),
    EMAIL_CONFIRMED(
            "email.confirmed",
            "<good>Linked: <value><address><good>. A forgotten password can be set again through it."),
    EMAIL_WRONG("email.wrong", "<etag:'ERROR'> <body>That is not the code. Tries left: <value><left><body>."),
    EMAIL_ALREADY_LINKED("email.already-linked", "<etag:'ERROR'> <body><value><address><body> is linked already."),
    EMAIL_REMOVED("email.removed", "<good><value><address><good> is no longer linked."),
    EMAIL_NOT_AN_ADDRESS("email.not-an-address", "<etag:'ERROR'> <body>That is not a mail address."),
    EMAIL_DOMAIN_REFUSED("email.domain-refused", "<etag:'ERROR'> <body>This server takes no address at that domain."),
    EMAIL_MAIL_OFF("email.mail-off", "<etag:'ERROR'> <body>This server sends no mail."),
    EMAIL_MAIL_FAILED("email.mail-failed", "<etag:'ERROR'> <body>The mail could not be sent. Try again later."),
    EMAIL_EXPIRED(
            "email.expired",
            "<etag:'ERROR'> <body>That code is too old. Ask a new one with <value>/email add" + " <address><body>."),
    EMAIL_NOTHING_PENDING(
            "email.nothing-pending",
            "<etag:'ERROR'> <body>No code waits. Ask one with <value>/email add <address><body>."),
    EMAIL_TOO_MANY_ATTEMPTS(
            "email.too-many-attempts",
            "<etag:'ERROR'> <body>The tries for that code are spent. Ask a new one with <value>/email"
                    + " add <address><body>."),
    EMAIL_NONE_LINKED("email.none-linked", "<etag:'ERROR'> <body>No address is linked."),
    EMAIL_FACTOR_ON(
            "email.factor-on",
            "<etag:'ERROR'> <body>Your login code goes to this address. Turn that off first with <value>/2fa email off"
                    + " <code><body>."),
    EMAIL_REQUIRED(
            "email.required",
            "<etag:'ERROR'> <body>This server keeps an address for a forgotten password. Link one with"
                    + " <value>/email add <address><body>, then <value>/email confirm <code><body> with the code"
                    + " it gets."),
    EMAIL_REQUIRED_KEEP(
            "email.required-keep",
            "<etag:'ERROR'> <body>This server requires an address, so it cannot be removed. Link another with"
                    + " <value>/email add <address><body> to replace it."),
    EMAIL_LOG_IN_FIRST("email.log-in-first", "<etag:'ERROR'> <body>Log in first."),
    RECOVER_SENT(
            "recover.sent",
            "<good>A code went to <value><address><good>. Type <value>/recover <code> <new"
                    + " password><good> within <value><minutes><good> minutes."),
    RECOVER_COOLDOWN(
            "recover.cooldown",
            "<etag:'ERROR'> <body>A code went out a moment ago. Ask again in <value><seconds><body> seconds."),
    RECOVER_NO_ADDRESS(
            "recover.no-address",
            "<etag:'ERROR'> <body>No address is linked to this account, so no code can be sent. Ask" + " the staff."),
    RECOVER_NOT_WAITING(
            "recover.not-waiting", "<etag:'ERROR'> <body>A password is recovered at the login, before it is typed."),
    RECOVER_USAGE(
            "recover.usage",
            "<etag:'ERROR'> <body>Type <value>/recover<body> for a code, then <value>/recover <code> <new"
                    + " password><body>."),
    RECOVER_DONE("recover.done", "<good>Your new password is set. Log in with <value>/login <password><good>."),
    RECOVER_WRONG("recover.wrong", "<etag:'ERROR'> <body>That is not the code. Tries left: <value><left><body>."),
    RECOVER_EXPIRED(
            "recover.expired", "<etag:'ERROR'> <body>That code is too old. Ask a new one with <value>/recover<body>."),
    RECOVER_NOTHING_PENDING(
            "recover.nothing-pending", "<etag:'ERROR'> <body>No code waits. Ask one with <value>/recover<body>."),
    RECOVER_TOO_MANY_ATTEMPTS(
            "recover.too-many-attempts",
            "<etag:'ERROR'> <body>The tries for that code are spent. Ask a new one with" + " <value>/recover<body>."),
    STAFF_UNKNOWN("staff.unknown", "<etag:'ERROR'> <body>No account is under <value><name><body>."),
    DATABASE_CONSOLE_ONLY(
            "database.console-only",
            "<etag:'ERROR'> <body>Only the console runs <value><command><body>: it changes where every account lives."),
    DATABASE_MOVE_STARTED(
            "database.move-started",
            "<tag:'AUTH'> <body>Copying every account to the database <value>storage.move-to<body> names."),
    DATABASE_MOVE_RUNNING(
            "database.move-running",
            "<etag:'ERROR'> <body>A move or a scheme change is running already. Wait for its answer."),
    DATABASE_MOVE_NO_TARGET(
            "database.move-no-target",
            "<etag:'ERROR'> <body><value>storage.move-to<body> names no database. Write the target's address there and run this again."),
    DATABASE_MOVE_PLAYERS_ONLINE(
            "database.move-players-online",
            "<etag:'ERROR'> <body>Players are online. Move the database while nobody plays."),
    DATABASE_MOVE_TARGET_FULL(
            "database.move-target-full",
            "<etag:'ERROR'> <body>The target holds this plugin's accounts already, and nothing was written. Name an empty database."),
    DATABASE_MOVE_DONE(
            "database.move-done",
            "<tag:'AUTH'> <body>Copied <value><rows><body> rows in <value><tables><body> tables. Copy the three lines of <value>storage.move-to<body> into <value>storage<body>, empty <value>storage.move-to<body>, and restart the server to finish the move."),
    DATABASE_MOVE_CHANGED(
            "database.move-changed",
            "<etag:'ERROR'> <body>The accounts changed while they were copied, so the target misses something. Empty the target and run this again while nobody plays."),
    DATABASE_SCHEME_UNKNOWN(
            "database.scheme-unknown",
            "<etag:'ERROR'> <body><value><scheme><body> is not a UUID scheme. Choose one of <value><schemes><body>."),
    DATABASE_SCHEME_STARTED(
            "database.scheme-started", "<tag:'AUTH'> <body>Moving every account to the <value><scheme><body> scheme."),
    DATABASE_SCHEME_PLAYERS_ONLINE(
            "database.scheme-players-online",
            "<etag:'ERROR'> <body>Players are online. Change the scheme while nobody plays."),
    DATABASE_SCHEME_DONE(
            "database.scheme-done",
            "<tag:'AUTH'> <body><value><moved><body> accounts moved, <value><unchanged><body> were in place and <value><left><body> were left. Write <value><scheme><body> in <value>ownership.uuid-scheme<body> and restart the server."),
    DATABASE_SCHEME_LEFT(
            "database.scheme-left",
            "<body>Left as they were, because another account takes the same id: <value><names><body>."),
    DATABASE_DOOR_SHUT(
            "database.door-shut", "<etag:'ERROR'> <body>The server is moving its accounts. Join again in a minute."),
    STAFF_CONSOLE_ONLY(
            "staff.console-only",
            "<etag:'ERROR'> <body>Run <value><command><body> from the console, or open it to staff in game under <value>staff.in-game<body> in config.conf."),
    STAFF_FORCED_LOGIN(
            "staff.forced-login",
            "<tag:'AUTH'> <body>Every server was told to let <value><name><body> in, wherever they wait."),
    STAFF_LET_IN("staff.let-in", "<tag:'AUTH'> <body>Staff let you in."),
    STAFF_UNBANNED("staff.unbanned", "<tag:'AUTH'> <body><value><address><body> is no longer banned."),
    STAFF_NOT_BANNED(
            "staff.not-banned",
            "<tag:'AUTH'> <body><value><address><body> was not banned. Its count of wrong passwords starts again."),
    STAFF_NOT_AN_ADDRESS("staff.not-an-address", "<etag:'ERROR'> <body><value><address><body> is no address."),
    STAFF_REMOVED(
            "staff.removed",
            "<tag:'AUTH'> <body>The account of <value><name><body> is removed, with everything kept under it."),
    STAFF_REMOVED_KICK("staff.removed-kick", "<body>Staff removed your account. Join again to register."),
    STAFF_PASSWORD_SET(
            "staff.password-set",
            "<tag:'AUTH'> <body>The password of <value><name><body> is set. Every session of the account ended."),
    STAFF_PASSWORD_RACED(
            "staff.password-raced",
            "<etag:'ERROR'> <body>The password of <value><name><body> changed while this one was set. Try again."),
    STAFF_REGISTERED("staff.registered", "<tag:'AUTH'> <body>An account is made for <value><name><body>."),
    STAFF_REGISTERED_YOU(
            "staff.registered-you",
            "<tag:'AUTH'> <body>Staff made your account. Log in with the password they gave you."),
    STAFF_NAME_TAKEN("staff.name-taken", "<etag:'ERROR'> <body>An account holds <value><name><body> already."),
    STAFF_NAME_REFUSED(
            "staff.name-refused", "<etag:'ERROR'> <body><value><name><body> breaks the name rules of config.conf."),
    STAFF_FACTOR_OFF(
            "staff.factor-off",
            "<tag:'AUTH'> <body>Every second factor of <value><name><body> is off. The next login asks the password only."),
    STAFF_FACTOR_NONE("staff.factor-none", "<tag:'AUTH'> <body><value><name><body> has no second factor."),
    STAFF_MARKED_PREMIUM(
            "staff.marked-premium",
            "<tag:'AUTH'> <body><value><name><body> is a premium account now. Its player joins again and proves the name."),
    STAFF_MARKED_CRACKED(
            "staff.marked-cracked",
            "<tag:'AUTH'> <body><value><name><body> is a password account now. Its player joins again and logs in with the password."),
    STAFF_MARK_ALREADY(
            "staff.mark-already",
            "<etag:'ERROR'> <body><value><name><body> is that kind of account already, or a kind this does not change."),
    STAFF_NOT_PAID("staff.not-paid", "<etag:'ERROR'> <body>No paid account holds the name <value><name><body>."),
    STAFF_NO_ANSWER(
            "staff.no-answer",
            "<etag:'ERROR'> <body>No source could say who holds <value><name><body> now. Try again in a while."),
    STAFF_PAID_TAKEN(
            "staff.paid-taken",
            "<etag:'ERROR'> <body>Another account holds the paid account of <value><name><body> already."),
    STAFF_NO_PASSWORD(
            "staff.no-password",
            "<etag:'ERROR'> <body><value><name><body> has no password to log in with. Set one first."),
    STAFF_REJOIN_KICK("staff.rejoin-kick", "<body>Staff changed how your account logs in. Join again."),
    ALERT_FAILED_LOGIN("alert.failed-login", "<tag:'AUTH'> <body>A wrong password came from <value><address><body>."),
    ALERT_BANNED(
            "alert.banned",
            "<tag:'AUTH'> <value><address><body> is banned for <value><time><body> after its wrong passwords."),
    ALERT_SHARED_ADDRESS(
            "alert.shared-address",
            "<tag:'AUTH'> <value><name><body> logged in from <value><address><body>, which <value><count><body> accounts have used."),
    ALERT_STAFF_ACTED(
            "alert.staff-acted",
            "<tag:'AUTH'> <value><actor><body> ran <value><action><body> on <value><target><body>."),
    ALERT_CONSOLE("alert.console", "the console"),
    PERF_HEADER("perf.header", "<tag:'AUTH'> <body>Time spent, over the last minute and since the start:"),
    PERF_LINE(
            "perf.line",
            "<value><kind><body>: <value><minute-count><body> times in the minute, <value><minute-average><body> ms on average, <value><minute-worst><body> ms at worst. Since the start <value><count><body> times, <value><average><body> ms on average, <value><worst><body> ms at worst."),
    PERF_HASHING("perf.hashing", "Hashing passwords"),
    PERF_DATABASE("perf.database", "The database"),
    PERF_MOJANG("perf.mojang", "Asking Mojang"),
    STAFF_RESOLVED_PREMIUM(
            "staff.resolved-premium",
            "<tag:'AUTH'> <body><value><name><body> goes to its paid owner. The cracked account moved to a marked name with everything it had."),
    STAFF_RESOLVED_CRACKED(
            "staff.resolved-cracked",
            "<tag:'AUTH'> <body><value><name><body> stays with its cracked holder. The paid owner is told to change their name."),
    STAFF_NO_DISPUTE("staff.no-dispute", "<etag:'ERROR'> <body>No dispute holds <value><name><body>."),
    INFO_HEADER("info.header", "<tag:'AUTH'> <value><name><body>, <value><kind><body>."),
    INFO_KIND_CRACKED("info.kind.cracked", "a password account"),
    INFO_KIND_PREMIUM("info.kind.premium", "a premium account"),
    INFO_KIND_BEDROCK("info.kind.bedrock", "a Bedrock account"),
    INFO_REGISTERED("info.registered", "<body>Registered <value><date><body> from <value><address><body>."),
    INFO_LAST_LOGIN(
            "info.last-login",
            "<body>Last login <value><date><body> from <value><address><body>, <value><logins><body>"
                    + " logins kept in all."),
    INFO_NEVER("info.never", "<body>No login is kept yet."),
    INFO_FACTORS(
            "info.factors",
            "<body>App: <value><app><body>. Backup codes left: <value><codes><body>. Code by mail:"
                    + " <value><mail><body>."),
    INFO_ON("info.on", "on"),
    INFO_OFF("info.off", "off"),
    INFO_ADDRESS("info.address", "<body>Mail address: <value><address><body>."),
    INFO_NO_ADDRESS("info.no-address", "<body>No mail address is linked."),
    INFO_ALTS("info.alts", "<body>Other accounts on its addresses: <value><alts><body>."),
    INFO_NO_ALTS("info.no-alts", "<body>No other account was seen on its addresses."),
    HISTORY_HEADER("history.header", "<tag:'AUTH'> <body>The latest logins of <value><name><body>:"),
    HISTORY_LINE("history.line", "<body><value><date><body> from <value><address><body>, by <value><method><body>."),
    HISTORY_EMPTY("history.empty", "<body>No login of <value><name><body> is kept."),
    METHOD_PASSWORD("method.password", "password"),
    METHOD_REGISTERED("method.registered", "registration"),
    METHOD_PREMIUM("method.premium", "premium"),
    METHOD_BEDROCK("method.bedrock", "Bedrock"),
    METHOD_SESSION("method.session", "session"),
    METHOD_FORCED("method.forced", "staff"),
    METHOD_NETWORK("method.network", "network"),
    MENU_LORE_DESCRIPTION("menu.lore.description", "Description"),
    MENU_LORE_DETAILS("menu.lore.details", "Details"),
    MENU_BUTTON_CLOSE("menu.button.close", "<accent>Close"),
    MENU_INFO_TITLE("menu.info.title", "<info_name>"),
    MENU_INFO_NONE("menu.info.none", "none"),
    MENU_INFO_ACCOUNT_TITLE("menu.info.account.title", "<info_name>"),
    MENU_INFO_ACCOUNT_CRUMB("menu.info.account.crumb", "Account"),
    MENU_INFO_ACCOUNT_DESCRIPTION("menu.info.account.description", "Who this is and where it began."),
    MENU_INFO_ACCOUNT_KIND_LABEL("menu.info.account.kind.label", "Kind"),
    MENU_INFO_ACCOUNT_KIND_VALUE("menu.info.account.kind.value", "<info_kind>"),
    MENU_INFO_ACCOUNT_REGISTERED_LABEL("menu.info.account.registered.label", "Registered"),
    MENU_INFO_ACCOUNT_REGISTERED_VALUE("menu.info.account.registered.value", "<info_registered>"),
    MENU_INFO_ACCOUNT_ORIGIN_LABEL("menu.info.account.origin.label", "From"),
    MENU_INFO_ACCOUNT_ORIGIN_VALUE("menu.info.account.origin.value", "<info_origin>"),
    MENU_INFO_LAST_TITLE("menu.info.last.title", "Last login"),
    MENU_INFO_LAST_CRUMB("menu.info.last.crumb", "Account"),
    MENU_INFO_LAST_DESCRIPTION("menu.info.last.description", "The newest login that is kept."),
    MENU_INFO_LAST_WHEN_LABEL("menu.info.last.when.label", "When"),
    MENU_INFO_LAST_WHEN_VALUE("menu.info.last.when.value", "<info_last>"),
    MENU_INFO_LAST_ADDRESS_LABEL("menu.info.last.address.label", "From"),
    MENU_INFO_LAST_ADDRESS_VALUE("menu.info.last.address.value", "<info_seen>"),
    MENU_INFO_LAST_COUNT_LABEL("menu.info.last.count.label", "Logins kept"),
    MENU_INFO_LAST_COUNT_VALUE("menu.info.last.count.value", "<info_logins>"),
    MENU_INFO_SECURITY_TITLE("menu.info.security.title", "Security"),
    MENU_INFO_SECURITY_CRUMB("menu.info.security.crumb", "Account"),
    MENU_INFO_SECURITY_DESCRIPTION("menu.info.security.description", "What it asks after the password."),
    MENU_INFO_SECURITY_APP_LABEL("menu.info.security.app.label", "App"),
    MENU_INFO_SECURITY_APP_VALUE("menu.info.security.app.value", "<info_app>"),
    MENU_INFO_SECURITY_BACKUP_LABEL("menu.info.security.backup.label", "Backup codes"),
    MENU_INFO_SECURITY_BACKUP_VALUE("menu.info.security.backup.value", "<info_backup>"),
    MENU_INFO_SECURITY_MAIL_LABEL("menu.info.security.mail.label", "Code by mail"),
    MENU_INFO_SECURITY_MAIL_VALUE("menu.info.security.mail.value", "<info_mail>"),
    MENU_INFO_SECURITY_ADDRESS_LABEL("menu.info.security.address.label", "Mail address"),
    MENU_INFO_SECURITY_ADDRESS_VALUE("menu.info.security.address.value", "<info_address>"),
    MENU_INFO_LOGIN_TITLE("menu.info.login.title", "<login_date>"),
    MENU_INFO_LOGIN_CRUMB("menu.info.login.crumb", "Login"),
    MENU_INFO_LOGIN_DESCRIPTION("menu.info.login.description", "One login the history keeps."),
    MENU_INFO_LOGIN_ADDRESS_LABEL("menu.info.login.address.label", "From"),
    MENU_INFO_LOGIN_ADDRESS_VALUE("menu.info.login.address.value", "<login_address>"),
    MENU_INFO_LOGIN_METHOD_LABEL("menu.info.login.method.label", "Way in"),
    MENU_INFO_LOGIN_METHOD_VALUE("menu.info.login.method.value", "<login_method>"),
    MENU_INFO_NO_LOGINS_TITLE("menu.info.no-logins.title", "No login yet"),
    MENU_INFO_NO_LOGINS_CRUMB("menu.info.no-logins.crumb", "Login"),
    MENU_INFO_NO_LOGINS_DESCRIPTION("menu.info.no-logins.description", "The history keeps no login\nof this account."),
    MENU_INFO_ALT_TITLE("menu.info.alt.title", "<alt_name>"),
    MENU_INFO_ALT_CRUMB("menu.info.alt.crumb", "Other account"),
    MENU_INFO_ALT_DESCRIPTION("menu.info.alt.description", "Seen on an address\nthis account used."),
    MENU_INFO_ALT_ACTION("menu.info.alt.action", "<cta>Click<subtext> to read this account"),
    MENU_INFO_NO_ALTS_TITLE("menu.info.no-alts.title", "No other account"),
    MENU_INFO_NO_ALTS_CRUMB("menu.info.no-alts.crumb", "Other account"),
    MENU_INFO_NO_ALTS_DESCRIPTION("menu.info.no-alts.description", "No other account was seen\non its addresses."),
    SESSION_LOG_IN_FIRST("session.log-in-first", "<etag:'ERROR'> <body>Log in first, then choose."),
    SESSION_NOT_CHOSEN(
            "session.not-chosen",
            "<etag:'ERROR'> <body>This server decides sessions for everyone, so there is nothing to choose."),
    CAPTCHA_NEEDED("captcha.needed", "<etag:'ERROR'> <body>Solve the captcha first."),
    CAPTCHA_WRONG("captcha.wrong", "<etag:'ERROR'> <body>That was not the code. Here is a new one."),
    CAPTCHA_SOLVED("captcha.solved", "<good>Captcha solved. Go on."),
    CAPTCHA_MAP("captcha.map", "<body>Read the code on the map in your hand and type <value>/captcha <code><body>."),
    CAPTCHA_MAP_CHAT("captcha.map-chat", "<body>Read the code on the map in your hand and type it in chat."),
    CAPTCHA_USAGE("captcha.usage", "<tag:'AUTH'> <body>Type the code after the command: <value>/captcha <code>"),
    CAPTCHA_NOTHING("captcha.nothing", "<etag:'ERROR'> <body>There is no captcha to answer."),
    LOGIN_MUST_REPLACE(
            "login.must-replace",
            "<body>Your password was right, but it no longer meets this server's rules. Set a new one with"
                    + " <value>/changepassword <current> <new><body> to come in."),
    LOGOUT_NOT_LOGGED_IN("logout.not-logged-in", "<etag:'ERROR'> <body>You are not logged in."),
    LOGOUT_SESSIONS_ENDED(
            "logout.sessions-ended",
            "<body>Your password was changed, so you were logged out. Log in with the new one."),
    ACCOUNT_LOG_IN_FIRST("account.log-in-first", "<etag:'ERROR'> <body>Log in first."),
    ACCOUNT_DELETE_ASK_PASSWORD(
            "account.delete-ask-password",
            "<tag:'AUTH'> <body>This deletes your account and everything kept under it. To go on, type <value>/unregister<body> and your password within <value><seconds><body> seconds."),
    ACCOUNT_DELETE_ASK_CODE(
            "account.delete-ask-code",
            "<tag:'AUTH'> <body>This deletes your account and everything kept under it. To go on, type <value>/unregister<body> and a code from your app within <value><seconds><body> seconds."),
    ACCOUNT_DELETE_WRONG(
            "account.delete-wrong",
            "<etag:'ERROR'> <body>That does not prove the account is yours. Nothing was deleted. Ask again to try once more."),
    ACCOUNT_DELETE_NOT_ASKED(
            "account.delete-not-asked",
            "<etag:'ERROR'> <body>There is nothing to confirm. Type <value>/unregister<body> alone first, then confirm in time."),
    ACCOUNT_DELETE_NO_PROOF(
            "account.delete-no-proof",
            "<etag:'ERROR'> <body>Your account has no password and no app to prove it is yours. Ask staff to delete it."),
    ACCOUNT_DELETED_KICK("account.deleted-kick", "<body>Your account is deleted. Join again to register a new one."),
    ACCOUNT_CRACKED_NOT_PREMIUM(
            "account.cracked-not-premium",
            "<etag:'ERROR'> <body>Your account is not premium, so there is nothing to switch."),
    ACCOUNT_CRACKED_WRONG("account.cracked-wrong", "<etag:'ERROR'> <body>That is not your password. Nothing changed."),
    ACCOUNT_DELETE_FORM_TITLE("account.delete-form-title", "Delete your account"),
    ACCOUNT_DELETE_FORM_PASSWORD(
            "account.delete-form-password", "Your password. This deletes your account and everything kept under it."),
    ACCOUNT_DELETE_FORM_CODE(
            "account.delete-form-code",
            "A code from your app. This deletes your account and everything kept under it."),
    ACCOUNT_CRACKED_USAGE(
            "account.cracked-usage",
            "<tag:'AUTH'> <body>This turns your account cracked: from your next join you log in with a password. Type <value>/cracked<body> and your password. If your account has none yet, what you type becomes it."),
    ACCOUNT_CRACKED_FORM_TITLE("account.cracked-form-title", "Switch to cracked"),
    ACCOUNT_CRACKED_FORM_PASSWORD(
            "account.cracked-form-password", "Your password. If your account has none yet, this becomes it."),
    ACCOUNT_LAST_LOGIN(
            "account.last-login",
            "<tag:'AUTH'> <body>Your last login was on <value><date><body> from <value><address><body>. If that was not you, change your password."),
    ACCOUNT_BACKUP_PASSWORD(
            "account.backup-password",
            "<tag:'AUTH'> <body>Your backup password, for a day Mojang cannot confirm you: <value><password><body>. Click it to copy and keep it safe, it is not shown again."),
    ACCOUNT_BACKUP_FORM_TITLE("account.backup-form-title", "Your backup password"),
    ACCOUNT_BACKUP_FORM_FIELD(
            "account.backup-form-field",
            "For a day Mojang cannot confirm you. Copy it and keep it safe, it is not shown again."),
    ACCOUNT_CRACKED_KICK(
            "account.cracked-kick", "<body>Your account is cracked now. Join again and log in with your password."),

    PASSWORD_TOO_SHORT(
            "password.too-short", "<etag:'ERROR'> <body>A password needs at least <value><min><body> characters."),
    PASSWORD_TOO_LONG(
            "password.too-long", "<etag:'ERROR'> <body>A password may have at most <value><max><body> characters."),
    PASSWORD_TOO_LONG_FOR_THE_HASH(
            "password.too-long-for-the-hash",
            "<etag:'ERROR'> <body>That password is too long to keep whole. Use fewer letters outside the English alphabet."),
    PASSWORD_COMMON(
            "password.common", "<etag:'ERROR'> <body>That password is one of the most common ones. Choose another."),
    PASSWORD_BREACHED(
            "password.breached",
            "<etag:'ERROR'> <body>That password is known from a data breach somewhere else. Choose another."),
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
    SCREEN_LOGIN_FORGOT("screen.login.forgot", "Forgot password"),
    SCREEN_LOGIN_CANCEL("screen.login.cancel", "Leave"),
    SCREEN_REPLACE_TITLE("screen.replace.title", "<value>A new password"),
    SCREEN_REPLACE_BODY(
            "screen.replace.body",
            "<body>Your password was right, but it no longer meets this server's rules. Choose a new one to come in."),
    SCREEN_REPLACE_PASSWORD("screen.replace.password", "New password"),
    SCREEN_REPLACE_CONFIRM("screen.replace.confirm", "The same password again"),
    SCREEN_REPLACE_SUBMIT("screen.replace.submit", "Save and come in"),
    SCREEN_REPLACE_CANCEL("screen.replace.cancel", "Leave"),
    SCREEN_CAPTCHA_TITLE("screen.captcha.title", "<value>Are you a player?"),
    SCREEN_CAPTCHA_BODY("screen.captcha.body", "<body>Type the code you see: <value><code>"),
    SCREEN_CAPTCHA_CODE("screen.captcha.code", "Code"),
    SCREEN_CAPTCHA_SUBMIT("screen.captcha.submit", "Continue"),
    SCREEN_CAPTCHA_CANCEL("screen.captcha.cancel", "Leave"),
    SCREEN_CODE_TITLE("screen.code.title", "<value>One more step"),
    SCREEN_CODE_BODY(
            "screen.code.body",
            "<body>Type the code your authenticator app shows for this server, the one mailed to you, or one of"
                    + " your backup codes."),
    SCREEN_CODE_CODE("screen.code.code", "Code"),
    SCREEN_CODE_SUBMIT("screen.code.submit", "Log in"),
    SCREEN_CODE_CANCEL("screen.code.cancel", "Leave"),
    SCREEN_FACTOR_TITLE("screen.factor.title", "<value>Authenticator app"),
    SCREEN_FACTOR_BODY(
            "screen.factor.body",
            "<body>Add an account in your authenticator app with the key below, then type its code."),
    SCREEN_FACTOR_KEY("screen.factor.key", "Key"),
    SCREEN_FACTOR_CODE("screen.factor.code", "Code from the app"),
    SCREEN_BACKUP_TITLE("screen.backup.title", "<value>Backup codes"),
    SCREEN_BACKUP_BODY(
            "screen.backup.body",
            "<body>Each code works once in place of the app's code. Copy them and keep them safe, they are not shown"
                    + " again."),
    SCREEN_BACKUP_CODES("screen.backup.codes", "Codes"),
    SCREEN_EMAIL_TITLE("screen.email.title", "<value>Mail address"),
    SCREEN_EMAIL_BODY(
            "screen.email.body", "<body>Type the address a forgotten password is sent to. A code goes to it first."),
    SCREEN_EMAIL_ADDRESS("screen.email.address", "Address"),
    SCREEN_EMAIL_CODE_BODY("screen.email.code-body", "<body>Type the code that went to the address."),
    SCREEN_EMAIL_CODE("screen.email.code", "Code"),
    SCREEN_RECOVER_TITLE("screen.recover.title", "<value>Forgotten password"),
    SCREEN_RECOVER_BODY("screen.recover.body", "<body>Type the code that went to your address and a new password."),
    SCREEN_RECOVER_CODE("screen.recover.code", "Code"),
    SCREEN_RECOVER_PASSWORD("screen.recover.password", "New password"),
    SCREEN_RECOVER_SUBMIT("screen.recover.submit", "Set password"),
    SCREEN_RECOVER_CANCEL("screen.recover.cancel", "Back"),
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
            "<body>Registering with <value>/register<body> means you accept these rules."),
    RULES_ACCEPTED_BY_CHAT("rules.accepted-by-chat", "<body>Choosing a password in chat means you accept these rules.");
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
