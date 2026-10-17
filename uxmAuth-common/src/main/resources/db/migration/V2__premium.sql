-- What the premium check writes down.
--
-- unverified  1 when the account was taken while no premium source could say who owns its name, so the
--             next connection under it asks the web again. 0 for every other account.
ALTER TABLE uxmauth_account ADD COLUMN unverified SMALLINT DEFAULT 0 NOT NULL;

-- One row per fact the plugin keeps about itself. uuid-scheme holds the UUID scheme of the first start,
-- which a later start under another scheme is refused against.
CREATE TABLE IF NOT EXISTS uxmauth_meta (
    meta_key   VARCHAR(64)  NOT NULL,
    meta_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (meta_key)
);
