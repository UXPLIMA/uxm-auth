# Changelog

## [0.2.0](https://github.com/UXPLIMA/uxm-auth/compare/uxm-auth-v0.1.0...uxm-auth-v0.2.0) (2026-11-06)


### ⚠ BREAKING CHANGES

* **common:** close the holes the security audit found ([#29](https://github.com/UXPLIMA/uxm-auth/issues/29))

### Features

* **common:** read German, Spanish, French, Portuguese and Russian ([#16](https://github.com/UXPLIMA/uxm-auth/issues/16)) ([1cb77a8](https://github.com/UXPLIMA/uxm-auth/commit/1cb77a8001ff47e0671f5c9c98008a9ebb69ec00))


### Bug Fixes

* **common:** close the holes the security audit found ([#29](https://github.com/UXPLIMA/uxm-auth/issues/29)) ([1acb1ad](https://github.com/UXPLIMA/uxm-auth/commit/1acb1ad8fb0fafc4c91cf553dc12f8caf1f27d89))

## [0.1.0](https://github.com/UXPLIMA/uxm-auth/compare/uxm-auth-v0.0.1...uxm-auth-v0.1.0)

### Changed

- The SQLite database lives under data/ and moves there by itself on the first start. The config copy is in backups/.
- Protection, ownership, effects and network live in their own files. An existing config.conf is split automatically on the first start, on servers and on the proxy.
- Discord.server in config.conf is now discord.server-name. The first start moves it.
- Call the Discord block's server name server-name.

### Fixed

- Point the plugin website at uxplima.com.
- Keep the operator's own maps when a start merges.

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1 under the UXPLIMA repository standard. Commands, permissions, configuration files and stored data stay as they were.
- The jar and a `SHA256SUMS` file are attached to every release.
