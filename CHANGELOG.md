# Changelog

## [0.2.0](https://github.com/UXPLIMA/uxm-auth/compare/uxm-auth-v0.1.0...uxm-auth-v0.2.0) (2026-11-06)

This release closes the holes a full security audit found. Read **Before you update** first if you run a network.

### Before you update

- On a network, update the proxy and every server at the same restart. Proxy claims, server reports and Redis bulletins have a new format, and members of the old and new releases refuse each other's (the console says `OUTDATED`).
- Every networked server now needs `network.server-name`, its name in the proxy's list. A server with the network on and no name refuses every login until it is set.
- Players who were remembered before the update type their password once more.

### Added

- Messages in German, Spanish, French, Portuguese and Russian.
- `protection.account-lock`: wrong passwords and codes count against the account they were typed for, from any address and through any filter, and lock it for a short, growing time. The owner back on their last address, premium and Bedrock logins and remembered sessions are not held back, and a password set with `/recover` lifts the lock. `failures = 0` turns it off.
- `protection.bans.failures-per-48` can ban a whole IPv6 /48. It ships at 0, off.
- `passwords.screen-logins`: a login answer longer than the rules allow or outside their characters is wrong at once, with no database read and no hash. Answers with control or invisible characters, or over 256 characters, are refused either way.
- `email.codes-per-day` and `email.recover-per-address-per-hour` limit how many codes are mailed.
- `ownership.lookups-per-address-per-10-minutes` caps the name lookups one address can cause.
- Forgiven address bans and ended sessions are cleared from the database once a day.
- A warning at start while the website table keeps passwords as SHA-256.

### Changed

- A player waiting at the login keeps what reached them while they waited, such as a vote reward or a crate key, on top of what they had.
- A remembered session cannot link an address or set up the authenticator app, on any server of a network. Log out and log in with the password first.
- A remembered session is also bound to the address the player typed to join.
- `/recover` answers the same whether or not the account has an address, and never shows it.
- Under the `strict` ownership rule a name no source can look up is refused for a moment, never taken as cracked. `when-every-source-fails = cracked` is read as refuse there, with a warning.
- Every connection from a shared-address source counts as through the filter, whatever host it typed.
- Staff commands run only from the console and RCON, not from command blocks.
- An import skips names that already hold an account, stops when its YAML file does not parse, and is not marked done when it read nothing.
- A start that fails for any reason refuses every login, and a failed reload sends online players away.

### Fixed

- Items could be duplicated through the login hold, and a waiting player could still use armour stands, break vehicles, edit books, use the recipe book and more. All of it is blocked now.
- A waiting player could run another plugin's command that shared a name with one of ours, or one of ours that was turned off.
- On a proxy with its own waiting world, a second connection under a waiting name could share the first one's login. It is turned away now.
- Proxy claims could be replayed or carried to another server, and a client could stop the server telling the proxy it logged out. Claims are now single-use and bound to one server.
- A paid owner could be locked out of their name by an account in another letter case, or by an id another account held.
- A recreated website row could take over the in-game account of the old one.
- When staff or a recovery changed a password, a player past the old one, waiting for a code or a new password, kept going; they go back to the password now.
- A waiting operator lost operator status when the server stopped.
- A player who registered could be logged in on a later connection under the same name; one client could flood the hashing queue with registrations; a premium lookup could undo a password change made meanwhile.
- The MaxMind country file never downloaded, so country rules stayed open.
- The purge could delete an account that had just logged in or was online.
- Start-up errors could print the database password or the Discord webhook.

### Removed

- The `recover.sent` and `recover.no-address` message lines. `recover.requested` replaces both.

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
