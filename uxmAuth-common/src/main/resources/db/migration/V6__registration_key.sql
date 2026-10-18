-- The key each account's registration address is counted under for the accounts-per-address
-- limit: the address itself for IPv4, its /64 for IPv6, as a home is handed a whole /64. An
-- account made before this column counts under the address it was made from.
ALTER TABLE uxmauth_account ADD COLUMN registration_key VARCHAR(45);
UPDATE uxmauth_account SET registration_key = registration_address;
CREATE INDEX uxmauth_account_registration_key ON uxmauth_account (registration_key);
