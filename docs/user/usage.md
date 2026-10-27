# Using the plugin

## Install

1. Put `uxmAuth-paper-<version>.jar` in the server's `plugins/` folder and start the server. The first start
   writes the files below into `plugins/uxmAuth/`.
2. Edit `config.conf`, then run `/uxmauth reload`. A broken edit leaves the running configuration as it was
   and the console says why.
3. Run `/uxmauth doctor`. It prints one line per thing that can be wrong: the database, the premium sources,
   PlaceholderAPI, mail and the rest.

A lone server needs nothing else. The default database is a SQLite file, `plugins/uxmAuth/auth.db`, and every
JDBC driver the plugin supports is in the jar.

A start that fails does not leave the server open: the plugin stays enabled with only a door that refuses
every login, and the console names the cause. Fix it and restart.

## Files

| File | What it holds |
|---|---|
| `config.conf` | Every setting. The proxy half on Velocity reads this same file. |
| `commands.conf` | The names, aliases and switches of every command, with Turkish words. A change needs a restart. |
| `screens.conf` | Where a waiting player stands and every dialog they read: register, login, code, recover, captcha, pin pad, rules. |
| `theme.conf` | Colours and glyphs of the interface. |
| `messages/messages_<language>.conf` | Every line a player reads. English and Turkish ship; a new file is a new language. |
| `mail/<kind>_<language>.txt` | The mails: `login-code`, `recover` and `verify`, one per language. |
| `menus/info.conf` | The staff window of `/uxmauth info`. |
| `passwords/common.txt` | Passwords too common to accept. |
| `imports/<source>.conf` | How each supported login plugin keeps its accounts. |

When a release adds a key, the next start adds it to the file with its comment and never changes a value you
wrote. The previous file is kept as `config.conf.bak`. A copy of each shipped message file is kept under
`messages/.defaults/`: a line you did not change takes a corrected wording from a new release, a line you
changed stays.

Settings that apply only at the next start are marked in `config.conf`: the `hashing` block, `lane.threads`
and `lane.max-threads`, `mail.threads`, `website-table`, and `commands.conf`.

## Where a waiting player stands

`waiting-room` in `screens.conf` chooses it:

- `dialog`: a dialog before the world, while the client is still connecting. Clients older than 1.21.6 fall
  back to `frozen`. Because the player does not exist yet, `uxmauth.login` and `uxmauth.register` count at the
  dialog only when the server grants them to everyone by default.
- `frozen`: in the world, held still, with the commands and the dialogs there.
- `login-world`: in an empty world of the plugin's own, `login-world-name`, moved back after the login.

`hold` in `config.conf` lists what a waiting player may not do (move, chat, commands, blocks, damage and so on)
and what is hidden from them. `timeouts` kicks a player who does not register or log in in time.
`chat-input` in `screens.conf` lets a player answer in plain chat instead of a command.

## Effects

`effects` in `config.conf` is a list of lines per moment: a join, a reminder, a wrong password, a registration, a
login. A line is `[message]`, `[actionbar]`, `[title]`, `[subtitle]`, `[bossbar]`, `[sound]`, `[particle]`,
`[effect]`, `[console]` or `[player]`. An `@name` is a line from the messages folder, so the words stay in every
language. `%player_name%`, `%player_uuid%` and `%uxmauth_time_left%` work in any line; other placeholders are
answered by PlaceholderAPI when it is installed.

## Languages

A player reads the language they chose with `/uxmauth lang set <language>`, else the one their client is set
to (`language.follow-client`), else `language.default`. `language.force` makes it one language for everybody.
Staff set another player's with `/uxmauth lang set-player`.

## Passwords

`hashing.algorithm` is the algorithm new passwords are written in: `argon2id`, `bcrypt`, `pbkdf2` or
`authme-sha256`. Every one is read whatever the setting says, and a player who logs in on an older one is moved
to the chosen one on the spot. `passwords` sets length, required character classes, the common-password file,
the number of wrong tries and an optional breach check that sends only the first five characters of the
password's SHA-1 to the Have I Been Pwned range service.

## Staff and alerts

`staff.in-game` names the destructive staff commands that may run in game. The others run only from the
console: `forcelogin`, `unregister`, `setpassword`, `disable2fa`, `markpremium`, `markcracked` and `resolve`.
Staff holding `uxmauth.admin.alerts` hear failed logins, bans, addresses many accounts use and staff actions, as
configured in `alerts`. `discord` posts the same kinds of events to one webhook; nothing is sent while its
`url` is empty.

## Privacy

`history` keeps every login with its time, address and way in for `history.keep-days`. It holds players'
addresses: mention it in the server's privacy policy.
