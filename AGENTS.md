# uxmAuth

Login and registration for offline-mode Paper, Folia and Velocity servers and networks: password accounts,
premium and Bedrock auto-login, sessions, second factor, address protection and imports. It never lets a
player who has not proved their name reach the game, and a failed start refuses every login instead of opening
the door.

## Commands

```sh
./gradlew build                      # format check, Error Prone, NullAway, jar verification
./gradlew spotlessApply              # format
./gradlew :uxmAuth-paper:runServer   # local Paper server with the plugin
./gradlew build -PprojectVersion=0.0.1 && ls */build/libs/*-0.0.1.jar   # the jars a release uploads
```

## Ways to hurt yourself

- **Killing by pattern.** Never kill `java` by name; stop `runServer` with its console or a PID you started.
- **Running staff commands on a shared database.** `/uxmauth unregister`, `database move` and `uuidscheme` change
  every server that reads it. Use a database of your own in `config.conf`.
- **Committing a secret.** `network.secret`, database passwords, the mail password, MaxMind keys and the Discord
  webhook URL stay out of git and out of docs.
- **Relocating `io.netty` in the Paper plugin jar.** The handshake needs the server's Netty. Lettuce's tree is
  relocated in `redisLibraries` first. `verifyAuthJar` guards this; do not weaken it.
- **Shading LimboAPI, Adventure or slf4j.** The proxy and the server own them.
- **Disabling the plugin on a failed start.** An offline server would admit anybody. `LockedDoor` refuses
  every login instead.
- **Blocking a server thread.** Database, web and mail go through the uxmLib `Scheduler`, never on a region or
  the global thread. `BukkitScheduler` is never used, so Folia keeps working.
- **Logging a password.** Login, register, password and 2FA commands are masked; a new command that takes a
  secret is masked too.

## Invariants

- `domain` imports no platform, Adventure, slf4j or Configurate type; `application` uses `domain` and its own
  `port` interfaces; adapters and `bootstrap` sit outside. `uxmAuth-common` imports no Bukkit or Velocity type.
- `uxmAuth-api` is carried unrelocated in both jars: other plugins compile against it. Keep it small and free of
  plugin internals.
- Every package is `@NullMarked`.
- No text a player reads is written in Java: it is a key with a line in every `messages/messages_<lang>.conf`.
- Every command and subcommand name comes from `commands.conf`.
- Configuration is read into one immutable snapshot and swapped whole on reload; a release's new keys merge into
  the operator's file without changing a value.
- A database change is a new `db/migration/V<n>__<name>.sql`, never an edit of an applied one.
- The UUID scheme is written to the database once and a start under another one is refused.
- LimboAPI 1.1 and 2 live in the `limboOne` and `limboTwo` source sets of `uxmAuth-velocity`; neither may import
  the other's types.
- `docs/internals/uxm-conventions.md`, `.editorconfig` and `.gitattributes` are synced from `UXPLIMA/.github`.

## Testing

There are no tests yet. New tests follow the repository standard: behavior through the public API, fakes for
ports, no assertion on structure, storage on every backend first. Visual and wording changes get a capture, not a
test. In-game behavior is checked with Craftwire on a development server.

## Pull requests

Titles are conventional commits with a scope: `api`, `common`, `handshake`, `paper`, `velocity`, `storage`,
`network`, `premium`, `build`, `ci`, `docs`, `release`. Before merging a release pull request, rewrite its
`CHANGELOG.md` entry for operators: Added, Changed, Fixed, Removed.
