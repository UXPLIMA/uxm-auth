# Changelog

## [0.1.0](https://github.com/UXPLIMA/uxm-auth/compare/uxm-auth-v0.0.1...uxm-auth-v0.1.0) (2026-11-01)


### ⚠ BREAKING CHANGES

* **storage:** the SQLite database lives under data/ and moves there by itself on the first start. The config copy is in backups/.
* **common:** protection, ownership, effects and network live in their own files. An existing config.conf is split automatically on the first start, on servers and on the proxy.
* **common:** discord.server in config.conf is now discord.server-name. The first start moves it.

### Features

* **storage:** keep the database in data/, config copies in backups/ ([#15](https://github.com/UXPLIMA/uxm-auth/issues/15)) ([1853f8f](https://github.com/UXPLIMA/uxm-auth/commit/1853f8f41ee1bd405bc91461118efcf60729b31b))


### Bug Fixes

* **build:** point the plugin website at uxplima.com ([#6](https://github.com/UXPLIMA/uxm-auth/issues/6)) ([70128ca](https://github.com/UXPLIMA/uxm-auth/commit/70128ca67b3c0b1015310be95d00b6c772dba1ec))
* **common:** keep the operator's own maps when a start merges ([#10](https://github.com/UXPLIMA/uxm-auth/issues/10)) ([991682a](https://github.com/UXPLIMA/uxm-auth/commit/991682a82d336417512673bfe6999fe4f7b471d3)), closes [#9](https://github.com/UXPLIMA/uxm-auth/issues/9)


### Code Refactoring

* **common:** call the Discord block's server name server-name ([#13](https://github.com/UXPLIMA/uxm-auth/issues/13)) ([feeae57](https://github.com/UXPLIMA/uxm-auth/commit/feeae57a097f57411cf3920b13be50ab508c11ef))
* **common:** give the four largest features their own files ([#14](https://github.com/UXPLIMA/uxm-auth/issues/14)) ([5b466bc](https://github.com/UXPLIMA/uxm-auth/commit/5b466bc73da9be8c4757549ce39b59b21e764961))

## 0.0.1 (2026-10-27)

### Changed

- Versions restart at 0.0.1 under the UXPLIMA repository standard. Commands, permissions, configuration files and stored data stay as they were.
- The jar and a `SHA256SUMS` file are attached to every release.
