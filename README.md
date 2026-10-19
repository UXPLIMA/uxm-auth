# uxmAuth

Login and registration for offline-mode Minecraft servers and networks, on Paper, Folia and Velocity.

A premium player never types a password. A Bedrock player never types a password. A cracked player
registers and logs in through a window, before they are in the world. Nobody can take a name that is not
theirs. One product, with no second plugin needed for any part of it.

## Which jar goes where

- A lone Paper or Folia server: the Paper jar.
- A Velocity network: the Velocity jar on the proxy, and the Paper jar on every backend.

## After the login, and the player's own account

- The join message waits for the login, and a player who never logged in leaves no quit message.
- The player reads the date and the masked address of their previous login.
- A premium player gets a backup password once, for the day Mojang cannot confirm them.
- Commands run after a registration and a login: on every server, on a named server, or on the proxy.
- A login spot, the way back to where the player stood, and a rescue out of a wall or a portal.
- `/unregister` deletes the player's own account, confirmed by the password or the app's code in time.
- `/cracked` turns a premium account cracked.
- Accounts nobody logged in to for a while are purged, when the operator turns it on.

## Answering in chat

`chat-input` in `screens.conf` lets a player in the world type their password, a new password and its repeat,
or the app's code as a plain line of chat, with no command. The line is taken before any other plugin, cancelled,
and reaches no player and no log. The player is told in chat what to type, and the commands keep working beside
it. Off by default.

## Placeholders

Published through PlaceholderAPI under the identifier `uxmauth`, when PlaceholderAPI is on the server; the
doctor says whether it is. Each answers for the player it is asked about, from memory, on whatever thread
asks. A player who has not logged in reads `false` for `authenticated` and nothing for the rest. The same
list is in the comment above the `placeholders` block of `config.conf`.

| Placeholder | Answers |
|-------------|---------|
| `%uxmauth_authenticated%` | `true` or `false` |
| `%uxmauth_method%` | how this session got in: `password`, `registered`, `premium`, `bedrock`, `session`, `forced` or `network` |
| `%uxmauth_premium%` | `true` or `false` |
| `%uxmauth_kind%` | `premium`, `cracked` or `bedrock` |
| `%uxmauth_2fa%` | `true` or `false`: an app, or a code by mail |
| `%uxmauth_registered%` | when the account was made, in `placeholders.date-format` |
| `%uxmauth_last_login%` | when this session logged in, in `placeholders.date-format` |

The second factor is read when the player logs in on this server and again whenever it changes here; a
change made on another server of a network shows at the player's next login.

## The developer API

Another plugin compiles against `com.uxplima:uxmauth-api` and finds the API through
`AuthApiProvider.get()`, on Paper and Velocity alike, or on Paper through the services manager. It answers
`isAuthenticated`, `method` and `kind` from memory. Both platform jars carry the module unrelocated.

On Paper, `PreLoginEvent` and `PreRegisterEvent` fire off the server's threads before a password is checked or
an account is made; `refuse(reason)` refuses with that reason, and a refused login spends no attempt.
`LoginEvent` and `RegisterEvent` fire on the player's thread; `FailedLoginEvent`, `BanEvent`,
`PasswordChangeEvent`, `UnregisterEvent` and `KindSwitchEvent` fire off the server's threads. On Velocity the
same names fire for what happens at the proxy: the two before, a login, a registration, a wrong password and a
ban.

## A website's login table

`website-table` in `config.conf` keeps a website's login table beside the plugin's own, in the shape many
website panels read. The website's row holds the password of a password account, so a change on either side
works on the other. An account the website made is adopted on its first lookup, with the offline UUID of its
name, and unverified, so the next connection asks whether a paid player owns the name. A row counts as an
account's only once the plugin wrote it, adopted it, or found the account's own hash in it. A row somebody made
on the website under the name of an account the plugin held first opens nothing and is never written. Premium
and Bedrock accounts never read or write a row, and a registration in game never writes over one. The plugin's
own row is still kept, so turning the table off loses only what the website changed since.

## Moving from another login plugin

`import { }` in `config.conf` names a source and where its database is. The files in the plugin's `imports/`
folder say how each source keeps its accounts; the plugin writes one for every source it knows, and a file of
your own describes any other table. Passwords keep their hash and move to the current algorithm at the next
login, authenticator secrets and premium ids come across, the source is only read, and an import runs once, at
the start, before anybody can join. A failed import stops the plugin with its reason and runs again at the next
start. `import.premium` reads a list of premium names after the accounts, and turns each cracked account it
names premium once the premium sources give that name the listed id.

## Moving the database, and changing the UUID scheme

`/uxmauth database move` copies every account to the database `storage.move-to` names, from the console while
nobody plays, and refuses a target that holds any. A restart onto the target completes the move.
`/uxmauth uuidscheme <scheme>` gives every account the id another UUID scheme gives it, from the console while
nobody plays; accounts that would share an id are named and left. Then write the scheme in
`ownership.uuid-scheme` and restart.

## Build

```bash
./gradlew spotlessApply
./gradlew build          # compile, static analysis, format check, tests, jar audit
./gradlew shadowJar      # the deployable jars, one per platform module
```

## Layout

- `uxmAuth-api`: what another plugin compiles against.
- `uxmAuth-common`: the domain, the use cases, the hashing, the settings and the catalogue. No Bukkit,
  no Velocity.
- `uxmAuth-handshake`: the part of a lone server's premium handshake that reaches into the server.
- `uxmAuth-paper`: the Paper and Folia plugin.
- `uxmAuth-velocity`: the Velocity plugin.
