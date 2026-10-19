# Changelog

## 0.0.3

- A start that fails, over a value the plugin cannot read, a changed UUID scheme or a failed import, now refuses
  every login with a reason and says why in the console. Before, the plugin disabled itself and an offline server
  let anybody in under any name. The proxy does the same.
- With chat answers on, a waiting captcha takes the chat line first, so its answer is never taken for a password,
  and the lines under the map and under the rules say to type in chat.
- A changed password is said in words: the shipped list tells the player, and that their remembered sessions
  ended.
- Under `ownership.rule = opt-in` the start says that only a player who typed `/premium` is proved, on a server
  and on the proxy.
- The chat prompt for the second step names the mailed code as well as the app's.

## 0.0.2

- A player in the world can answer in chat, with no command: the password, a new password and its repeat, or the
  app's code. Turned on with `chat-input` in `screens.conf`. The line reaches no player and no log.
- A boss bar an effect line puts up while the player waits comes down at the login or the registration, and a
  bar line can drain over its own seconds with `drain`.

## 0.0.1

The first release. It carries everything below, on Paper, Folia and Velocity.

### Who a player is

- A name is checked against Mojang before anything else, through sources the operator lists and orders.
  The operator decides what an unknown name is when every source fails.
- A premium player is proved by the real handshake, on a lone server and on a network, and keeps their skin.
  A server or proxy in online mode is refused, and the doctor says why.
- Four ways to own a name: the first comer keeps it, opt-in with `/premium`, strict, or by domain.
- Four answers when a premium owner finds their name taken: the cracked account is renamed, both are
  stopped for staff, the cracked owner keeps it, or the premium owner takes it.
- Four UUID schemes, and a console command that moves every account to another one.

### Where a player waits

- A dialog before the world, the frozen world with commands and dialogs, a login world of its own, an auth
  server on a network, or a virtual world on the proxy.
- A client too old to draw a dialog waits in the frozen world with the commands, whatever the operator chose.
- A Bedrock player gets native forms for every screen.

### Passwords and the second factor

- Register, login, change and logout, with rules the operator writes.
- Argon2id by default, BCrypt, PBKDF2 and the common salted SHA-256 format, and every imported hash
  verifies. A hash moves to the current algorithm at the next login.
- A common-password file, an optional breach check that sends only five characters of a hash, and a weak
  old password replaced at the next login.
- An authenticator app, set up by text, link or a QR code on a map, with backup codes. A code by mail for a
  player without an app. The second factor can be forced by a permission node the operator names.
- Recovery by a code sent to a confirmed mail address, through the operator's own SMTP server.

### Sessions and abuse

- Sessions by address and time, chosen by the player or on for everybody, across a whole network, and off
  for the domains the operator names.
- A kick after wrong passwords, address bans that grow with each repeat, limits on connections and on
  accounts per address, countries allowed or denied, and time limits to register and to log in.
- A captcha on a map, in a dialog, or in a Bedrock form.

### Staff

- Account info with alts, login history, live alerts, and a performance view.
- Force a login or a registration, delete an account, set a password, turn off the second factor, mark an
  account premium or cracked, unban, and settle a disputed name.
- Destructive commands run from the console only, by default.

### What the player sees and manages

- Every moment of the login runs an effect list the operator writes: messages, titles, action bars, boss
  bars, sounds and commands, with its own list for each way in.
- The rules screen before registration, a PIN pad, the date and masked address of the last login, and a
  backup password for a premium player.
- Every screen and message in the player's own language. English and Turkish ship, and a new language is a
  new file.
- Commands after a registration and a login, on every server or a named one, or on the proxy.
- A login spot, a return to where the player stood, and a rescue out of a wall or a portal.
- `/unregister` deletes the player's own account, `/cracked` and `/premium` switch it, and inactive accounts
  are purged when the operator turns it on.

### Running it

- SQLite, H2, MySQL, MariaDB and PostgreSQL. A console command moves every account to another database.
- Importers from the other login plugins, and a premium list read after them.
- A website's login table kept beside the plugin's own.
- PlaceholderAPI placeholders, Discord webhooks, a developer API with events, and Redis for a network.
- Every configuration file gains the keys a new release adds, and nothing the operator wrote changes.
- Every command can be renamed, aliased, given words in another language, or turned off.
