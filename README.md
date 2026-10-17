# uxmAuth

Login and registration for offline-mode Minecraft servers and networks, on Paper, Folia and Velocity.

A premium player never types a password. A Bedrock player never types a password. A cracked player
registers and logs in through a window, before they are in the world. Nobody can take a name that is not
theirs. One product, with no second plugin needed for any part of it.

## Which jar goes where

- A lone Paper or Folia server: `uxmAuth-paper`.
- A Velocity network: `uxmAuth-velocity` on the proxy, and `uxmAuth-paper` on every backend.

`docs/08-auth.md` describes what the plugin does. `uxm-briefs/uxm-auth.md` is the product it is built to.

## Build

```bash
./gradlew spotlessApply
./gradlew build          # compile, static analysis, format check, tests, jar audit
./gradlew shadowJar      # the deployable jars, one per platform module
```

## Layout

- `uxmAuth-common`: the domain, the use cases, the hashing, the settings and the catalogue. No Bukkit,
  no Velocity.
- `uxmAuth-paper`: the Paper and Folia plugin.
- `uxmAuth-velocity`: the Velocity plugin.

`docs/adr/0003-three-gradle-modules.md` records why.
