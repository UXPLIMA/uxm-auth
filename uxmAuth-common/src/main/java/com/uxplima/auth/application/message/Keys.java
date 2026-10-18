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
    JOIN_CODE_TITLE("join.code-title", "<value>One more step"),
    JOIN_CODE_SUBTITLE("join.code-subtitle", "<body>Type your login code with <value>/2fa"),
    REMINDER_REGISTER("reminder.register", "<body>Register with <value>/register <password> <password>"),
    REMINDER_LOGIN("reminder.login", "<body>Log in with <value>/login <password>"),
    REMINDER_CODE("reminder.code", "<body>Type your login code with <value>/2fa <code>"),
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
    SESSION_LOG_IN_FIRST("session.log-in-first", "<etag:'ERROR'> <body>Log in first, then choose."),
    SESSION_NOT_CHOSEN(
            "session.not-chosen",
            "<etag:'ERROR'> <body>This server decides sessions for everyone, so there is nothing to choose."),
    CAPTCHA_NEEDED("captcha.needed", "<etag:'ERROR'> <body>Solve the captcha first."),
    CAPTCHA_WRONG("captcha.wrong", "<etag:'ERROR'> <body>That was not the code. Here is a new one."),
    CAPTCHA_SOLVED("captcha.solved", "<good>Captcha solved. Go on."),
    CAPTCHA_MAP("captcha.map", "<body>Read the code on the map in your hand and type <value>/captcha <code><body>."),
    CAPTCHA_NOTHING("captcha.nothing", "<etag:'ERROR'> <body>There is no captcha to answer."),
    LOGIN_MUST_REPLACE(
            "login.must-replace",
            "<body>Your password was right, but it no longer meets this server's rules. Set a new one with"
                    + " <value>/changepassword <current> <new><body> to come in."),
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
