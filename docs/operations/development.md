# Development

The build comes from `uxm-gradle`. The root project builds nothing; it declares `uxm.java` without applying it
so every module resolves the plugins the conventions bring, and it carries the group and the one version.
Versions come from the `uxm` catalog; `gradle/libs.versions.toml` holds only what that catalog lacks: the
Velocity and LimboAPI APIs, Adventure, Gson, BouncyCastle, Lettuce, MaxMind, ZXing, the JDBC drivers, Log4j and
the paperweight plugin.

| Module | Plugins applied | Output |
|---|---|---|
| `uxmAuth-api` | `uxm.java` | A jar inside both plugin jars. |
| `uxmAuth-common` | `uxm.java` | A jar inside both plugin jars. |
| `uxmAuth-handshake` | `uxm.java`, paperweight userdev | A jar inside the Paper jar. |
| `uxmAuth-paper` | `uxm.paper-plugin` | `uxmAuth-paper-<version>.jar`, shipped. |
| `uxmAuth-velocity` | `uxm.java`, Shadow | `uxmAuth-velocity-<version>.jar`, shipped. |

## Build and run

```sh
./gradlew build                      # format, compile with checks, jar verification
./gradlew spotlessApply              # format
./gradlew :uxmAuth-paper:runServer   # a local Paper server with the plugin, in run/
```

`build` runs `verifyJar` and `verifyAuthJar` on the Paper jar and `verifyJar` on the Velocity jar. They fail when a
bundled library is not relocated, when Adventure or slf4j is shaded, or when a part the plugin needs is missing:
the JDBC drivers, Lettuce, MaxMind, ZXing, the API, and on the proxy both LimboAPI worlds.

Compile warnings fail the build (`-Werror` with Error Prone and NullAway). Every package is `@NullMarked`.

## Checking in game

Use Craftwire on a development setup, never a production one. `plugin_deploy` builds and installs the Paper
jar; bots then run `/register` and `/login`, and the console and exceptions tell what broke. The proxy half is
checked on a development proxy with a LimboAPI release for each line it supports.

## Release

release-please keeps a release pull request open on `main`. Before merging it, rewrite its `CHANGELOG.md` entry
for operators and keep the pull request body's first and last lines, which release-please reads. Merging it tags
`uxm-auth-v<version>`. The release workflow builds with `./gradlew build -PprojectVersion=<version>` and attaches
the Paper jar, the Velocity jar and a `SHA256SUMS` file to the GitHub release. It copies the jars of
`uxmAuth-paper` and `uxmAuth-velocity` only; the other modules' jars are not shipped.
