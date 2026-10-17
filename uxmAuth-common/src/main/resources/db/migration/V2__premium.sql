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

-- One row per move of an account from one name to another, so the player is told at their next login and
-- a connection under the old name reads where it went.
--
-- old_key  the old name in lower case, which a connection under it is matched by.
-- told     1 once the account's player has read that it moved, 0 until then.
CREATE TABLE IF NOT EXISTS uxmauth_rename (
    account_id VARCHAR(36) NOT NULL,
    old_key    VARCHAR(16) NOT NULL,
    old_name   VARCHAR(16) NOT NULL,
    new_name   VARCHAR(16) NOT NULL,
    renamed_at BIGINT      NOT NULL,
    told       SMALLINT    DEFAULT 0 NOT NULL
);

CREATE INDEX uxmauth_rename_old_key ON uxmauth_rename (old_key);
CREATE INDEX uxmauth_rename_account ON uxmauth_rename (account_id);

-- One row per name a cracked account holds and a paid owner proved, while the operator stops both until
-- staff settle it. premium_id is the Mojang id that proved the name.
CREATE TABLE IF NOT EXISTS uxmauth_dispute (
    name_key   VARCHAR(16) NOT NULL,
    premium_id VARCHAR(36) NOT NULL,
    raised_at  BIGINT      NOT NULL,
    PRIMARY KEY (name_key)
);
