# Moving accounts

## From another login plugin

`import` in `config.conf` brings accounts over once, at the start, before anybody can join.

- `source` names a file in the `imports/` folder: `authme`, `nlogin`, `openlogin`, `librelogin`, `limboauth`,
  `jpremium`, `loginto`, `authy`, `dynamicbungeeauth`, `loginsecurity` or `authplus`. Each file says how that
  plugin keeps its accounts, and a file of your own describes any other table. Empty imports nothing.
- `jdbc-url`, `username` and `password` are the database the source keeps its accounts in. A file database is
  named by its path, such as `jdbc:sqlite:plugins/AuthMe/authme.db`. The source is only read, never written. A
  source that keeps a file of its own, such as `authplus`, needs none of the three.
- `table` reads another table than the file names. `jpremium` and `dynamicbungeeauth` name none, so set it for
  those.

Passwords keep their hash and move to `hashing.algorithm` at the player's next login. Authenticator secrets and
premium ids come across. The start prints how many accounts came over and how many were refused, never a
password. An import runs once; every later start says it ran and does nothing. A failed import stops the plugin
with its reason, marks nothing and runs again at the next start. On a network every server may name the same
source: the first to start imports and the others wait and find it done.

`import.premium` reads a list of premium names another plugin kept, such as `fastlogin`, after the accounts, with
the same five keys. It makes no account. A cracked account becomes premium under its Mojang id only when the
premium sources give the name that id, and it keeps its password. A name they cannot place stays as it was.

## A website's login table

`website-table` keeps a website's login table beside the plugin's own, in the shape many website panels read.
With `enabled = true`, every password account is written there too, in the format `hash` names (`sha256`,
`bcrypt`, `pbkdf2` or `argon2`), and that table is where a password lives: a change on the website works at the
next login, a change in game works on the website. The table is made in the plugin's database with the column
names of `columns` when it is missing; an existing one is used as it is. Everything applies at the next start.

- An account the website made is adopted at its first lookup with the offline UUID of its name, unverified, so
  the next connection asks whether a paid player owns the name.
- A row counts as an account's only once the plugin wrote it, adopted it, or found the account's own hash in it.
  A row someone made on the website under the name of an account the plugin held first opens nothing and is never
  written.
- Premium and Bedrock accounts never read or write a row, and a registration in game never writes over one.
- Removing an account in game removes its row. The plugin's own row is still kept, so turning the table off loses
  only what the website changed since.
- `sha256` is weak: choose it only for a website that reads nothing else.

## Moving the database

1. Fill `storage.move-to` with the target database, empty of this plugin's tables. A target that holds an
   account is refused and nothing is written.
2. Run `/uxmauth database move` from the console while nobody plays. The website table moves too when it is on.
3. Put the target's three lines in place of `storage.jdbc-url`, `username` and `password`, empty `move-to`, and
   restart.

To change the UUID scheme instead, see [premium names](premium.md#uuid-schemes).
