# One account with Blysis

[Blysis](https://github.com/INOVIXI/blysis) is a website for Minecraft servers. With the `blysis` block of
`config.conf` on, a Blysis member and a password account in game are one account: one name and one password,
kept by the website. Blysis' `"User"` table is where the password lives. Nothing is copied to a second table, and
a player links nothing by hand.

## Turning it on

```hocon
blysis {
  enabled         = true
  jdbc-url        = "jdbc:postgresql://10.0.0.5:5432/blysis"
  username        = "uxmauth"
  password        = "..."
  pool-size       = 4
  timeout-seconds = 5
  member-role     = "member"
  email           = "{name}@players.example.com"
  locale          = "en"
  currency        = "USD"
  hash            = "bcrypt"
}
```

- `jdbc-url`, `username` and `password` are the Blysis database. It has a pool of its own, so this plugin's own
  tables stay in the `storage` block, on SQLite, MariaDB or any other database. Reach the Blysis database over a
  private network, not the internet.
- The database user needs `SELECT`, `INSERT` and `UPDATE` on `"User"`, `SELECT` on `"Role"` and `INSERT` on
  `"UserRole"`, and nothing else:

  ```sql
  GRANT SELECT, INSERT, UPDATE ON "User" TO uxmauth;
  GRANT SELECT ON "Role" TO uxmauth;
  GRANT INSERT ON "UserRole" TO uxmauth;
  ```

- `hash` is the format the website writes, set in its security settings: `bcrypt` (Blysis' default) or `scrypt`.
  Set the same one here. Two different settings would rewrite each other's hash at every login on the other side.
  `bcrypt` uses `hashing.bcrypt.cost`, which Blysis writes as 12.
- `website-table` and `blysis` cannot both be on. A start with both on refuses every login and says why.
- Everything in the block applies at the next start.

## What a player sees

- **A member of the website joins.** They join under their username, in the case they registered it on the
  website, and log in with their website password. The plugin's own row is made at that first join, under the
  offline UUID of the name. It is unverified, so the next connection asks whether a paid player owns the name,
  as for any account.
- **A new player registers in game.** `/register` makes a Blysis member: the name as the username, the password
  in the website's format, `locale`, `currency`, and the `member-role` role. Blysis requires a unique email, so the
  member gets the address `email` names, with `{name}` as the name in lower case. Point it at a domain you own, or
  at `.invalid`, which no mail reaches. The player can change it on the website later.
- **A name the website holds is taken.** A name any member holds, in any case, cannot be registered in game. A
  member who signs in only with another service (Discord, Google) has no password, so their name opens nothing in
  game and cannot be registered either. They set a password on the website first.
- **Passwords move both ways.** A password changed on the website works at the next login in game. A password
  changed in game (`/changepassword`, `/uxmauth setpassword`) is written to the member.
- **Old hashes.** A member whose hash is not in the `hash` format (scrypt, or AuthMe's `$SHA$` from a website
  that imported old accounts) logs in, and the hash is rewritten in the `hash` format at that moment. The write
  happens only while the member still holds the hash that was checked, so a password changed on the website at
  the same moment is kept.
- **Banned and deleted members.** A member banned (`isBanned`) or deleted on the website cannot log in. The right
  password gets "This account is banned or closed on the website"; a wrong one counts as wrong, so a guess learns
  nothing. A remembered session does not let such a member in either. A member deleted outright from the table is
  treated the same.
- **Case.** Two members whose names differ only in case, from before Blysis held a name to one person, are told
  apart by the exact spelling. A spelling that matches more than one member and none exactly opens nothing, as on
  the website's own sign in.
- **A member renamed on the website** keeps their account in game under the old name: an account is tied to the
  member's id, not to the username. The new username does not open a second account.
- **An account moved to another name in game**, as when the paid owner of a name proves it and the cracked
  account moves aside, keeps its member, whose username on the website stays as it was. A name another member
  holds is refused for such a move, as for a registration.

## Accounts the plugin held before

An account the plugin had before Blysis was on keeps its password. At its next login:

- When no member holds its name, it becomes a member, with its current password.
- When a member holds its name with the same hash, as one copied from this plugin does, the two are linked and
  the member's password counts from then on.
- When a member holds its name with another hash, the member is somebody else: they registered the name on the
  website after the player held it here. The account keeps its own password and the member opens nothing in game.
  Settle it on the website, for example by renaming that member.

Premium and Bedrock accounts never read or write a member.

## Removing an account

A player's own `/unregister`, staff's `/uxmauth unregister` and the purge of inactive accounts remove only the
plugin's own row and what it keeps (second factor, mail, sessions). The member stays on the website with their orders, history and password. A
member that still has a password is the account again at the next join under the name, so an unregister resets
the game side of the account and nothing more. Neither nulling nor deleting the member is done here, because the
member's website login and purchase history live there, and because a member made in game has a placeholder
address and could not reset a lost password by mail.

- To **close** an account, ban or delete the member on the website.
- To **reset a forgotten password**, use `/uxmauth setpassword` or the website's own tools.

## When the website's database does not answer

- **At the start:** the start fails and every login is refused, as when the plugin's own database does not
  answer. Start again once the database answers.
- **Later:** a join whose name cannot be checked is kept out with the database message, a login or a
  registration that cannot reach the database fails, and nobody is let in unchecked. A name whose members cannot
  be read is never taken for a free one, so nobody can register over a member while the database is away.
  `timeout-seconds` is how long a lookup waits.
- A player who has an account here already and is premium or Bedrock is not affected. A name that has no account
  here yet is checked against the members first, so a new player is kept out too, premium or not.

## Performance

A name is looked up with `LOWER("username") = LOWER(?)`, which the unique index on `"username"` does not serve.
On a site with many members, an index on the lower-case name makes each join one index read:

```sql
CREATE INDEX "User_username_lower_idx" ON "User" (LOWER("username"));
```
