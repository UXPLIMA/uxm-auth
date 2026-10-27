# Network guide

On a network, uxmAuth runs on the Velocity proxy and on every Paper server behind it. The proxy runs the
premium check, knows who has logged in and tells each server so with a claim it signs. A server takes a claim
only with the right signature, so a player who reaches it some other way is asked for the password as usual.

## Install

1. Put the Velocity jar on the proxy and the Paper jar on every backend server. The proxy half reads the same
   `config.conf` as a server, so one file can be copied to every machine. It uses the `language`, `storage`,
   `premium`, `ownership` and `network` blocks; when players wait in its own world it also uses `names`,
   `passwords`, `hashing`, `lane` and `timeouts`, and reads the words of `/login` and `/register` from
   `commands.conf`.
2. Point `storage.jdbc-url` of the proxy and of every server at one MySQL, MariaDB or PostgreSQL database, so an
   account is the same account everywhere.
3. Start the proxy. With `network.secret` empty it makes a secret and writes it to `network-secret.txt` in its
   data folder, readable by its owner only, and says where in its console.
4. Copy that secret into `network.secret` on every backend server, set `network.enabled = true` and set
   `network.server-name` to the server's name in the proxy's list. Keep the secret private: whoever has it can
   log anybody in. A server with the network on and no secret refuses to start.

Leave `network.enabled` off on a server that stands alone.

## Routing

On the proxy:

- `network.auth-servers`: where a player waits until logged in. Empty means they wait at the dialog on whichever
  server they join and stay there.
- `network.lobbies`: where a player goes from an auth server once logged in, and where a player logged in by
  Mojang goes first.
- `network.balance`: how a server is picked from a list: `least-players`, `random` or `first`.
- `network.after-login`: `lobby`, `last-server` or `by-domain`, which uses the `by-domain` list of
  `{ host = "eu.example.org", server = lobby-eu }` entries, a star standing for one part of a name.
- `network.allowed-commands`: proxy commands a player may run before logging in. Every other proxy command, and
  every move to another server, waits for the login.

`network.claim-seconds` is how long a signed claim stays good; the proxy writes a fresh one before each
connection to a server.

## Redis

Redis carries what every proxy and server must hear at once, such as an account whose sessions ended after a
password change. Every member writes the same `network.redis.uri` (for example `redis://:password@host:6379/0`)
and `network.redis.channel`. With an empty `uri` the proxy alone holds the logins and each server reads the
database.

## The proxy's waiting world

`network.wait-in` on the proxy chooses where a player waits before logging in:

- `server`: the dialog or the frozen world of the server they join, so every server behind the proxy must run
  uxmAuth.
- `limbo`: a world built inside the proxy that no server sees, where the player types `/login` or `/register`.
  It needs LimboAPI on the proxy, version 1.1 or 2. `network.limbo-file` names a schematic in the data folder
  the world is built from; without it the player stands on a small platform at `network.limbo-spawn`.

A proxy asked for its own world that cannot build it, because LimboAPI is missing or is a release the plugin does
not know, refuses every login and says why in its console. A server without uxmAuth would let a waiting player
straight in, and the proxy never lets that happen. With the proxy's world, a player who has not logged in reaches
no server, not even the first.

## Staff alerts and commands

Alerts reach staff holding `uxmauth.admin.alerts` on every server of the network. `/uxmauth forcelogin` and the
other staff commands act on the account wherever its player waits. A ban made at the proxy is not sent to Discord;
one made on a server is. The `[console]` and `[player]` lines of the `registered` and `logged-in` effect lists run
once per login on the proxy, however many servers the player visits afterwards.
