# Premium names and ownership

A name that exists at Mojang can be held by a paid player or by a player without a paid account. These
settings decide who gets the name and what happens when two players want it. They live in the `premium` block of
`config.conf` and in `ownership.conf`.

## How a name is checked

`premium.sources` lists the services asked, in order: `mojang`, `playerdb`, `minetools`, `ashcon`. The first that
can answer decides; one that is down, slow or refusing passes the name to the next. An answer is kept for
`premium.cache-minutes`; an answer no source could give is never kept. `premium.mojang-cap-per-10-minutes`
keeps requests under Mojang's limit so the mirrors answer the rest. `premium.addresses` points a source at a
mirror of your own.

One address may have the sources asked about `ownership.lookups-per-address-per-10-minutes` names it has not
asked about lately; past that a new name from it counts as one no source could answer. Players behind a
shared-address filter are not counted. On a lone server the address rules (countries, bans, connections per
address) are met at the first packet that names the player, before any source is asked, so a refused address
never spends Mojang's cap.

A premium player who has no password gets a random one at the first premium login, shown once and stored as a
hash (`premium.backup-password`). With `ownership.premium-password-fallback` it lets the player in when the
handshake fails because Mojang is down or the Mojang account is lost.

## Ownership rules

`ownership.rule` is one of:

| Rule | Behavior |
|---|---|
| `automatic` | A new name that exists at Mojang is asked for the handshake once. A paid player is in without a password. A player who fails joins with a password from the next connection and keeps the name until its paid owner proves it with `/claim` or the button on the login screen. |
| `opt-in` | Everybody joins with a password. A player who types `/premium` is asked for the handshake from the next connection on. |
| `strict` | Every name that exists at Mojang must pass the handshake. A cracked player can never hold a paid name. |
| `domain` | The address a player joins through decides: `ownership.domains.premium` for paid accounts, `ownership.domains.cracked` for the rest. An address on neither list counts as cracked. A cracked name gets `cracked-mark` at `mark-at`. |

Names are held once whatever their case. A client of a paid account always sends Mojang's spelling, so where the
web is asked a new name in another spelling of a paid one (`notch` for `Notch`) is refused: nobody under it could
pass the handshake, and its paid owner would be sent to that spelling forever. When an account here already holds
a name in another case, a connection in Mojang's own spelling is asked for the handshake under every rule but
`domain`, and once it passes the holder meets `ownership.conflict` like any other.

`when-every-source-fails` says what a new name is taken for when no source could answer: `cracked` (marked
unverified and asked again next time), `premium` or `refuse`. Under `strict` a name no source could answer is never
taken for cracked, because sources that are down or a Mojang cap spent by a flood of names would otherwise let a
cracked player take a paid name: `cracked` is read as `refuse` there and the start says so, and a cracked account
the web cleared when it was made keeps logging in with its password.

`failed-memory-seconds` remembers a connection that failed the handshake so it is told why. `claim-minutes` is
how long a claim waits for the next connection from the same address.

Behind a shared-address filter (`protection.shared-address-*`, TCPShield and the like) every player arrives from
the filter's address, so neither memory can tell them apart. There `/claim` and `/premium` are not taken (staff
settle the name with `/uxmauth markpremium` or `/uxmauth resolve`), and a failure remembered for the filter's
address may let a player in with a password but never turns one away.

The handshake itself runs on a lone server only in offline mode and not behind a proxy. In online mode the
server proves every player, and behind a proxy the network's handshake belongs to the proxy; the console says
which applies.

## UUID schemes

`ownership.uuid-scheme` decides the UUID a player plays under, which every other plugin uses to key
inventories, ranks and money:

| Scheme | Behavior |
|---|---|
| `keep-first` | A new paid player gets the Mojang UUID, a new cracked player the offline one. An account keeps the UUID it was made with. |
| `offline` | Everybody gets the offline UUID derived from the name. |
| `mojang` | Every paid player gets the Mojang UUID. A cracked account that later proves a paid name moves to it. |
| `random` | Every new player gets a random UUID, kept from then on. |

An account that moved away from a name keeps its UUID. A new player under that name, cracked or paid, whose
scheme would give them that same UUID gets a random one nobody holds instead, so they never open the moved
account's inventory, rank or operator status.

The scheme is written into the database at the first start. A later start under another scheme is refused,
because it would put every player into an empty inventory. To change it, run `/uxmauth uuidscheme <scheme>` from
the console while nobody plays, write the scheme in `ownership.uuid-scheme` and restart. Accounts that would
share an id are named and left.

## Name conflicts

When the paid owner of a name proves it while a cracked account holds it, `ownership.conflict` decides:

| Strategy | Behavior |
|---|---|
| `rename` | The cracked account moves with everything it had to the name plus `rename-mark`. The paid owner gets the name. |
| `block` | Both are stopped with a message until staff settle it with `/uxmauth resolve`. |
| `cracked-keeps` | The cracked account keeps the name and the paid owner is told to change theirs at Mojang. |
| `premium-takes` | The cracked account is deleted and the paid owner gets the name. Whoever buys a name takes the account of the player who held it first. |

`rename-mark` must pass `names.pattern`, and the plugin refuses a mark it does not take. The default `_` passes
Paper's own name check. A cracked mark such as `+` is refused by Paper while `perform-username-validation` is on
in `config/paper-global.yml`; turn that off or use a letter mark. The start warns when the two do not fit.

## Bedrock

With `bedrock.auto-login`, a Bedrock player that Floodgate vouched for through Xbox Live enters without a
password, into their own Bedrock account or the Java account Floodgate linked them to. An unlinked Bedrock
player under a Java account's name is still asked for that account's password. Off, every Bedrock player uses a
password through a native form.
