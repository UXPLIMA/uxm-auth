# Overview

uxmAuth decides who a connecting player is and keeps everyone who has not proved it out of the game. A lone
server and a network decide that in two different places, so the code is split into modules where one holds
every rule and the others translate between a platform and those rules.

## Modules

| Module | Holds | Depends on |
|---|---|---|
| `uxmAuth-api` | What another plugin compiles against: `AuthApi`, `AuthApiProvider`, the Paper and Velocity events. | The platform APIs, `compileOnly`. |
| `uxmAuth-common` | The domain, the use cases, hashing, settings, storage, mail, geo lookup, premium sources, the message catalogue and every shipped resource. No Bukkit, no Velocity. | `api`, uxmLib, Configurate, the database drivers, Lettuce, BouncyCastle, MaxMind, ZXing. |
| `uxmAuth-handshake` | The part of a lone server's premium handshake that reaches into the server's own login classes, compiled with paperweight against the Paper dev bundle. | `common`. |
| `uxmAuth-paper` | The Paper and Folia plugin and its jar. | `common`, `handshake`, `api`, uxmLib. |
| `uxmAuth-velocity` | The Velocity plugin and its jar, with the proxy's waiting world for LimboAPI 1.1 (`limboOne`) and 2 (`limboTwo`). | `common`, `api`, uxmLib. |

The release ships the Paper jar and the Velocity jar. The other three modules reach players inside them.

## Layers in `common`

| Package | Holds | May use |
|---|---|---|
| `domain` | Accounts, names, passwords, hashes, factors, premium and protection rules, network claims, session state. | The JDK. |
| `application` | Use cases (`LogIn`, `RegisterAccount`, `ChangePassword`, `Sessions`), the ports they need (`application.port`) and the message keys. | `domain`. |
| `adapter.outbound` | Port implementations: the database, settings, mail, premium sources, geo files, imports, health checks. | `application`, libraries. |

Both platform modules add `adapter.inbound` (commands, listeners, screens, placeholders) and `bootstrap`, the
composition root: `AuthPlugin` owns the Bukkit lifecycle and `PluginModule` builds every object; the proxy has
`AuthVelocityPlugin` and `ProxyModule`. `api` imports no platform type outside its `paper` and `velocity`
packages. `domain` and `application` import no Adventure, slf4j, Configurate or Typesafe type.

## Threading

- No call blocks a server thread. Database, web and mail work goes through the uxmLib `Scheduler`, which routes to
  the global, region, entity or async scheduler, so Folia works. `BukkitScheduler` is never used.
- Hashing runs in `HashingLane`, a bounded pool: `lane.threads` workers, growing to `lane.max-threads`, with
  `lane.queue` waiting. A login past the queue is told to try again rather than left hanging. Each Argon2id hash
  holds `hashing.argon2id.memory-kib`, so the lane bounds the memory hashing can take.
- Placeholders answer from the session in memory and never from the database, because PlaceholderAPI asks on
  whatever thread it is on.
- The door decision of the handshake can wait on the database and the web, so its answer arrives on another
  thread and is carried back to the connection's event loop before anything touches the channel.

## Storage

Accounts live behind uxmLib's storage module: SQLite by default, MySQL, MariaDB, PostgreSQL or H2 by URL.
Migrations are the `db/migration/V<n>__<name>.sql` resources of `common`, applied in order at start. The UUID
scheme is written to the database at the first start and a later start under another scheme is refused. The
website table is a second table in the same database, written beside the plugin's own row. Wrong-password
bans, sessions and the login history are kept in the same database, so every server of a network sees them.

## Configuration

`config.conf`, `commands.conf`, `screens.conf` and `theme.conf` are HOCON read into immutable snapshots. A
reload builds a new snapshot and swaps it in only when every file parsed. A release's new keys are merged into
the operator's file at the next start, keeping every value the operator wrote. No text a player reads is written
in Java: it is a key with a line in every `messages_<language>.conf`, and a screen or an effect names the key
with `@name`.

## The handshake and Netty

A lone server in offline mode can ask a client to prove its name through Mojang. `Handshake` puts
`HelloInterceptor` on every new connection through Paper's channel initialize listener. The interceptor holds the
first hello until the door decides, then asks the client for its key or hands the hello to the server as it came.
Anything a client sends while its hello waits, a second hello above all, closes the connection. A door that fails
refuses the connection at pre-login; it never lets it in unchecked. In online mode the server proves players
itself, and behind a proxy the proxy does; the handshake installs nothing there and the console says why.

## Traps

- **Two Nettys in one jar.** Lettuce brings Netty and Reactor and must be relocated; the handshake must keep naming
  the server's own `io.netty`. A single `relocate("io.netty", ...)` in the plugin jar rewrites both and the server
  then refuses the channel listener at every connection. The Paper build relocates Lettuce's tree in a separate
  `redisLibraries` jar first and the plugin jar takes that jar whole; `verifyAuthJar` fails when the handshake names
  the relocated Netty.
- **Signed BouncyCastle.** The jar's signature files are excluded from every shaded jar, because a signature that
  names relocated classes no longer verifies and the JVM refuses the jar.
- **A failed start must not open the door.** `AuthPlugin` registers a `LockedDoor` that refuses every login when the
  graph cannot be built. Never leave the plugin disabled on a failure: an offline server would then admit anybody.
- **Two LimboAPI lines.** LimboAPI 1.1 and 2 cannot share a classpath, so the proxy's waiting world is compiled in
  the `limboOne` and `limboTwo` source sets and both are packed into the one jar. LimboAPI is never shaded in.
- **Commands and passwords in logs.** Login, register and password commands are masked in the console and the log,
  and a Log4j filter on the server keeps a password out of the log.
- **The UUID scheme is a one-way door.** Changing it by editing the file is refused; only the console command moves
  accounts safely.
