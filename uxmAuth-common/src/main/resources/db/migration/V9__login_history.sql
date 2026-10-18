-- Every login, with when, from where and how. address is as the server read it; address_key is the address as
-- the bans count it (an IPv6 home by its /64), which is how the accounts on one address are found. Kept for
-- history.keep-days.
CREATE TABLE IF NOT EXISTS uxmauth_login (
    account_id  VARCHAR(36) NOT NULL,
    at          BIGINT      NOT NULL,
    address     VARCHAR(64) NOT NULL,
    address_key VARCHAR(64) NOT NULL,
    method      VARCHAR(16) NOT NULL
);
CREATE INDEX uxmauth_login_account ON uxmauth_login (account_id, at);
CREATE INDEX uxmauth_login_address ON uxmauth_login (address_key, at);
