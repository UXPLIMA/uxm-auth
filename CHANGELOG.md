# Changelog

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
