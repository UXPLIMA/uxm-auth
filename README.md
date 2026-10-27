# uxmAuth

Login and registration for offline-mode Minecraft servers and networks, on Paper, Folia and Velocity.

A premium player never types a password. A Bedrock player never types a password. A cracked player
registers and logs in through a window before they are in the world. Nobody can take a name that is not
theirs. One product, with no second plugin needed for any part of it.

## Which jar goes where

- A lone Paper or Folia server: `uxmAuth-paper-<version>.jar` in `plugins/`.
- A Velocity network: `uxmAuth-velocity-<version>.jar` on the proxy and the Paper jar on every backend
  server. See [the network guide](docs/user/network.md).

Both jars come from the [releases page](https://github.com/UXPLIMA/uxm-auth/releases) with a `SHA256SUMS`
file. Paper 26.3 and Java 25 are required. PlaceholderAPI, Vault, ViaVersion, Floodgate and LimboAPI are
optional: the plugin runs without any of them.

## What it does

- Registers and logs players in with a password in a dialog before the world, in the frozen world, or in an
  empty login world, and in chat when the operator turns that on.
- Proves premium names through Mojang and mirrors, so a paid player joins without a password and a name has
  one owner.
- Logs in Bedrock players through Floodgate and shows them native forms.
- Remembers sessions, adds an authenticator app or a mailed code as a second factor, recovers a forgotten
  password by mail.
- Bans addresses after wrong passwords, rate-limits connections and registrations, filters countries and
  shows a captcha.
- Imports accounts from other login plugins, shares a website's login table, and moves a whole database.
- Offers PlaceholderAPI placeholders, a developer API and events.

## Build

```sh
./gradlew build
```

The jars are `uxmAuth-paper/build/libs/uxmAuth-paper-<version>.jar` and
`uxmAuth-velocity/build/libs/uxmAuth-velocity-<version>.jar`.

## Documentation

[docs/README.md](docs/README.md)
