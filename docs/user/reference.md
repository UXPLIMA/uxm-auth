# Reference

Command names below are the defaults; `commands.conf` renames, aliases or disables each one. A player command
works for a player who has not logged in yet, and the password after it is kept out of the console and the log.

Contents: [Player commands](#player-commands) · [Staff commands](#staff-commands) · [Permissions](#permissions) ·
[Placeholders](#placeholders) · [Events and API](#events-and-api)

## Player commands

| Command | Aliases | Permission | What it does |
|---|---|---|---|
| `/login [password]` | `/l`, `/log` | `uxmauth.login` | Logs in with the password. |
| `/register [password] [confirm]` | `/reg` | `uxmauth.register` | Makes an account. |
| `/changepassword [old] [new]` | `/changepass`, `/cp` | `uxmauth.changepassword` | Changes the password. |
| `/logout` | | `uxmauth.logout` | Logs out and waits at the door again. |
| `/claim` | | `uxmauth.claim` | Says the name is yours; the next connection from the same address is asked to prove it through Mojang. |
| `/premium` | | `uxmauth.premium` | Proves the name on the next connection and logs in without a password from then on. |
| `/cracked [password]` | | `uxmauth.cracked` | Turns a premium account cracked. |
| `/unregister [proof]` | | `uxmauth.unregister` | Deletes the player's own account: alone it asks, then again with the password or an app code within `account.delete-confirm-seconds`. |
| `/session on\|off` | | `uxmauth.session` | Chooses whether a join from the same address skips the password. Off ends every session of the account. |
| `/captcha [code]` | | `uxmauth.captcha` | Answers the captcha. |
| `/recover [code] [password]` | | `uxmauth.recover` | Alone it mails a code to the linked address; with the code and a new password it sets that password. |
| `/email add [address]` | | `uxmauth.email` | Mails a code to the address. |
| `/email confirm [code]` | | `uxmauth.email` | Links the address with its code. |
| `/email remove` | | `uxmauth.email` | Takes the linked address away. |
| `/2fa [code]` | | `uxmauth.2fa` | Answers the second-factor code. |
| `/2fa setup` | | `uxmauth.2fa` | Shows the key, the link and the QR code on a map. |
| `/2fa confirm [code]` | | `uxmauth.2fa` | Turns it on with the app's first code and shows the backup codes. |
| `/2fa backup [code]` | | `uxmauth.2fa` | Shows a new set of backup codes and voids the old set. |
| `/2fa email on` / `/2fa email off [code]` | | `uxmauth.2fa` | Mails the login code instead of asking the app, or stops it. |
| `/2fa off [code]` | | `uxmauth.2fa` | Turns it off with an app code or a backup code. |

Every command also has Turkish words, for example `/giris`, `/kayit`, `/cikis`, `/oturum`, `/dogrulama`,
`/eposta`, `/kurtar`, `/sahiplen`, `/hesabisil` and `/crackedol`. They work for anyone who types them and are
listed to players who read Turkish.

## Staff commands

The root is `/uxmauth`, alias `/auth`.

| Command | Permission | What it does |
|---|---|---|
| `/uxmauth` | `uxmauth.admin.version` | Says what the command is. |
| `/uxmauth version` | `uxmauth.admin.version` | Shows the running version. |
| `/uxmauth reload` | `uxmauth.admin.reload` | Reads the configuration and the content again. |
| `/uxmauth doctor` | `uxmauth.admin.doctor` | One line per check, and whether anything needs an operator. |
| `/uxmauth info <player>` | `uxmauth.admin.info` | Dates, logins, factors, address and the other accounts on its addresses. `/uxmauth bilgi` in Turkish. |
| `/uxmauth history <player>` | `uxmauth.admin.history` | The latest logins with time, address and way in. |
| `/uxmauth perf` | `uxmauth.admin.perf` | Time spent hashing, querying the database and asking Mojang. |
| `/uxmauth forcelogin <player>` | `uxmauth.admin.forcelogin` | Lets a waiting player in past the login, on whichever server they wait. |
| `/uxmauth unban <address>` | `uxmauth.admin.unban` | Lifts the ban on an address and forgets its wrong passwords. |
| `/uxmauth unregister <player>` | `uxmauth.admin.unregister` | Removes an account and everything kept under it. |
| `/uxmauth setpassword <player> <password>` | `uxmauth.admin.setpassword` | Sets a password and ends the account's sessions. |
| `/uxmauth forceregister <player> <password>` | `uxmauth.admin.forceregister` | Makes a password account under a name. |
| `/uxmauth disable2fa <player>` | `uxmauth.admin.disable2fa` | Turns off every second factor of an account. |
| `/uxmauth markpremium <player>` | `uxmauth.admin.markpremium` | Turns a cracked account premium when Mojang names a paid account under its name. |
| `/uxmauth markcracked <player>` | `uxmauth.admin.markcracked` | Turns a premium account cracked. |
| `/uxmauth resolve <player> <side>` | `uxmauth.admin.resolve` | Settles a name a cracked holder and a paid owner both want: `premium` or `cracked`. |
| `/uxmauth database move` | `uxmauth.admin.database` | Copies every account to `storage.move-to`. Console only, while nobody plays. |
| `/uxmauth uuidscheme <scheme>` | `uxmauth.admin.database` | Gives every account the id another UUID scheme gives it. Console only, while nobody plays. |
| `/uxmauth lang` | `uxmauth.language` | Says the language the player reads and what else exists. |
| `/uxmauth lang set <language>` | `uxmauth.language` | Reads the plugin in one language. |
| `/uxmauth lang reset` | `uxmauth.language` | Follows the client again. |
| `/uxmauth lang set-player <player> <language>` | `uxmauth.admin.language` | Sets another player's language. |
| `/uxmauth lang reset-player <player>` | `uxmauth.admin.language` | Clears another player's choice. |

Besides the permission, `forcelogin`, `unregister`, `setpassword`, `disable2fa`, `markpremium`, `markcracked`
and `resolve` run only from the console unless `staff.in-game` lists them. The Velocity jar registers the
`/login` and `/register` words of `commands.conf` for the proxy's waiting world only.

## Permissions

| Permission | Default |
|---|---|
| `uxmauth.language`, `uxmauth.login`, `uxmauth.register`, `uxmauth.logout`, `uxmauth.claim`, `uxmauth.premium`, `uxmauth.unregister`, `uxmauth.cracked`, `uxmauth.captcha`, `uxmauth.session`, `uxmauth.2fa`, `uxmauth.recover`, `uxmauth.email`, `uxmauth.changepassword` | everyone |
| `uxmauth.admin.*` | op, grants the whole `uxmauth.admin` family |
| `uxmauth.admin.doctor`, `.version`, `.reload`, `.info`, `.history`, `.forcelogin`, `.unban`, `.unregister`, `.setpassword`, `.forceregister`, `.disable2fa`, `.markpremium`, `.markcracked`, `.resolve`, `.alerts`, `.perf`, `.database`, `.language` | op |
| `uxmauth.2fa.required` | not declared; `second-factor.required-permission` names it. A player holding it outright plays only with the app on. The default an operator gets does not count. |

## Placeholders

Published through PlaceholderAPI under `uxmauth` when it is installed. Each answers for the player asked about,
from memory, on whatever thread asks. A player who has not logged in reads `false` for `authenticated` and
nothing for the rest.

| Placeholder | Answers |
|---|---|
| `%uxmauth_authenticated%` | `true` or `false` |
| `%uxmauth_method%` | how the session got in: `password`, `registered`, `premium`, `bedrock`, `session`, `forced` or `network` |
| `%uxmauth_premium%` | `true` or `false` |
| `%uxmauth_kind%` | `premium`, `cracked` or `bedrock` |
| `%uxmauth_2fa%` | `true` or `false`: an app, or a code by mail |
| `%uxmauth_registered%` | when the account was made, in `placeholders.date-format` |
| `%uxmauth_last_login%` | when the session logged in, in `placeholders.date-format` |

`%uxmauth_time_left%`, the seconds a waiting player has before the timeout, works only inside `effects` lines.

## Events and API

Another plugin compiles against the `uxmAuth-api` module and reaches the API through `AuthApiProvider.get()` on
Paper and Velocity, or through the services manager on Paper. It answers `isAuthenticated`, `method` and `kind`
from memory. Both jars carry the module unrelocated.

On Paper, `PreLoginEvent` and `PreRegisterEvent` fire off the server's threads before a password is checked or
an account is made; `refuse(reason)` refuses with that reason and a refused login spends no attempt.
`LoginEvent` and `RegisterEvent` fire on the player's thread. `FailedLoginEvent`, `BanEvent`,
`PasswordChangeEvent`, `UnregisterEvent` and `KindSwitchEvent` fire off the server's threads. On Velocity
`PreLoginEvent`, `PreRegisterEvent`, `LoginEvent`, `RegisterEvent`, `FailedLoginEvent` and `BanEvent` fire for
what happens at the proxy.
