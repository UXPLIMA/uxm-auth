# Protection and second factor

Everything here is in `config.conf`. A reload applies it unless the block says otherwise.

## Sessions

`sessions.mode` is `off` (every join asks the password), `everyone` (every login is remembered) or `chosen`
(only a player who ticked the box, used the Bedrock toggle or typed `/session on`). A player back from the same
address within `sessions.minutes` skips the password. A password change, `/logout`, `/session off` and a new
address end the session. `sessions.skip-second-factor` decides whether a session also skips the app code.

## Address bans

`protection.bans` bans the address of wrong passwords. Every `failures` wrong passwords earn the next step of
`steps-minutes`, and the last step repeats. An address quiet for `forgive-after-hours` starts again from the first
step. An IPv6 address counts by its /64. `/uxmauth unban <address>` lifts a ban. `passwords.max-attempts` kicks a
player after that many wrong passwords on one connection.

## Limits

- `protection.connections`: how many connections one address may make inside `window-seconds`, counted at every
  connection whatever comes of it. A refused connection counts too.
- `protection.accounts`: how many password accounts one address may make inside `window-hours`. Premium and
  Bedrock players are proved already and neither counted nor refused.
- `exempt-addresses` in both takes one address or a range with its prefix, for your own proxies, or a village
  or school that shares one IPv4 address.

## Shared addresses

Behind a filter such as TCPShield every player arrives from the filter's address. `protection.shared-address-hosts`
and `shared-address-sources` name the hosts the filter answers for and the addresses it connects from. A
connection counts as through the filter only when one of those addresses made it, and sessions and every rule
that reads the address are off for it. With hosts and no sources nothing counts and the console says so at
every start. When the filter hands the server each player's own address, use that and leave both empty.

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
wrong password does and counts toward the address bans. `premium-skips` lets a proven premium or Bedrock player
skip the code. `required-permission` (default `uxmauth.2fa.required`) holds a player until the app is set up,
for as long as they hold the node outright; give it to staff through a permission plugin.

## Mail and linked addresses

Nothing is mailed while `mail.enabled` is false. `mail` names the SMTP server (`host`, `port`, `security` of
`plain`, `ssl` or `starttls`, login, sender). The mail texts are the files in `mail/`, one per kind and
language. `email` governs the linked address: `allowed-domains`, `denied-domains`, the code's lifetime, tries,
cooldown and length, and `required-at-registration`, which holds a password account until it links a confirmed
address (it holds nobody while mail is off). Mail failures are told to the player and once to the console, never
with the address.

## Purge

`purge` removes accounts nobody logged in to for `after-days`, with everything kept under them, checking every
`check-hours`. It is off by default and `keep-premium` spares premium accounts.
