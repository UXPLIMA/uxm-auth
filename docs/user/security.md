# Protection and second factor

Everything here is in `config.conf`, except the `protection` block, which has its own `protection.conf`. A reload
applies it unless the block says otherwise.

## Sessions

`sessions.mode` is `off` (every join asks the password), `everyone` (every login is remembered) or `chosen`
(only a player who ticked the box, used the Bedrock toggle or typed `/session on`). A player back from the same
address within `sessions.minutes` skips the password. A password change, `/logout`, `/session off` and a new
address end the session. `sessions.skip-second-factor` decides whether a session also skips the app code.

## Address bans

`protection.bans` bans the address of wrong passwords. Every `failures` wrong passwords earn the next step of
`steps-minutes`, and the last step repeats. An address quiet for `forgive-after-hours` starts again from the first
step. An IPv6 address counts by its /64, and with every other address of its /48 too: `failures-per-48` wrong
passwords across a /48 ban the whole /48, so hopping between the /64s of one provider block does not escape a
ban. `/uxmauth unban <address>` lifts a ban, the /48's with it. `passwords.max-attempts` kicks a
player after that many wrong passwords on one connection.

## Account locks

`protection.account-lock` counts wrong passwords, app codes, backup codes and mailed login codes against the
account they were typed for, from any address and through any filter, so changing address or hiding behind a
shared filter does not buy more guesses. Every `failures` of them lock the account for the next step of
`steps-minutes` (the last step repeats, none may pass a day), and while it is locked no password or code is
checked for it: the player reads when to try again, the same whether the password was right or not. A try counts
before it is checked, so tries sent together cannot pass the limit, and a right one gives it back. A finished
login starts the count again, and an account quiet for `forgive-after-hours` starts from the first step. A wrong
old password in `/changepassword` and a wrong proof in `/unregister` count too.

A stranger can lock somebody else's account on purpose, so the lock is a short, growing delay and never a ban:
premium and Bedrock logins and remembered sessions are never held back, nor is the owner back on the address
they last logged in from (its /64 for IPv6) unless a filter shares it, and the right password works as soon as
the lock runs out. The cost is that an owner who types their password from somewhere new may wait a few minutes
while somebody is guessing at their account.

## Limits

- `protection.connections`: how many connections one address may make inside `window-seconds`, counted at every
  connection whatever comes of it. A refused connection counts too.
- `protection.accounts`: how many password accounts one address may make inside `window-hours`. Premium and
  Bedrock players are proved already and neither counted nor refused.
- `exempt-addresses` in both takes one address or a range with its prefix, for your own proxies, or a village
  or school that shares one IPv4 address.

## Shared addresses

Behind a filter such as TCPShield every player arrives from the filter's address. `protection.shared-address-sources`
names the addresses the filter connects from. Every connection one of them made counts as through the filter,
whatever host the player typed, since any player behind it may type any host; sessions and every rule that reads
the address are off for it. `shared-address-hosts` is kept from older releases and narrows nothing: with hosts
and no sources nothing counts, and with both the console asks you to empty the hosts. When the filter hands the
server each player's own address, use that and leave both empty.

## Countries

`protection.countries.mode` is `off`, `allow` (only listed countries come in) or `deny` (listed countries stay
out). `list` holds two-letter codes, `allow-addresses` lets addresses in whatever their country, `unknown` is
what to do with an address the file cannot place. The data is MaxMind's free GeoLite2 country file: with
`account-id` and `licence-key` it is fetched into the data folder and refreshed every `refresh-days`; without
them put `GeoLite2-Country.mmdb` there yourself. With no file every country is let in and the console says so.

## Captcha

`protection.captcha.when` is `off`, `register`, `failures` or `both`. `after-failures` wrong passwords on a name
bring it, forgotten after `failure-memory-minutes`. `java` is `dialog` (the code as text) or `map` (drawn under
noise on a map in the hand, answered with `/captcha`). The map needs `hold.hide-inventory` on, otherwise the
dialog is shown. Premium and Bedrock players never meet it.

## Second factor

A player turns on an authenticator app with `/2fa setup` and `/2fa confirm`, or a code by mail with
`/2fa email on`. `second-factor` sets the issuer, the label (`{player}` is the name), the digits (6 to 8), the
period (15 to 300 seconds), the window steps, the setup time and the backup codes. A wrong code costs a try as a
wrong password does and counts toward the address bans, the account lock and the captcha of the name. `premium-skips` lets a proven premium or Bedrock player
skip the code. `required-permission` (default `uxmauth.2fa.required`) holds a player until the app is set up,
for as long as they hold the node outright; give it to staff through a permission plugin.

## Mail and linked addresses

Nothing is mailed while `mail.enabled` is false. `mail` names the SMTP server (`host`, `port`, `security` of
`plain`, `ssl` or `starttls`, login, sender). The mail texts are the files in `mail/`, one per kind and
language. `email` governs the linked address: `allowed-domains`, `denied-domains`, the code's lifetime, tries,
cooldown and length, and `required-at-registration`, which holds a password account until it links a confirmed
address (it holds nobody while mail is off). Mail failures are told to the player and once to the console, never
with the address.

When a code went is kept apart from the code, so a code that is spent, used up or past its time keeps its
cooldown, and `codes-per-day` caps how many codes of one kind an account is sent in a day; login codes, which only
a right password sends, keep the cooldown but are not capped. `/recover` answers an ask the same way whether the
account has an address, a code went, or the cooldown or cap held one back, and never shows the address, so
nobody learns from it which accounts have one. One address may ask `recover-per-address-per-hour` times an hour
over every account (not counted through a shared filter). The time an ask takes can still differ, since a code is
hashed and mailed only for an account with an address.

## Purge

`purge` removes accounts nobody logged in to for `after-days`, with everything kept under them, checking every
`check-hours`. It is off by default and `keep-premium` spares premium accounts.
