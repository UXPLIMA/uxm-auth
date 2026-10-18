-- The accounts whose row in a website's login table is known to be theirs: the plugin wrote that row, adopted it,
-- or found the account's own hash in it. A row under an account's name that is not linked is somebody else's,
-- and gives the account nothing.
CREATE TABLE IF NOT EXISTS uxmauth_website_link (
    account_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (account_id)
);
