-- Which website row a link is bound to. row_mark is a SHA-256, in hex, of what tells the row the link was made
-- for from a row made later under the same name: its registration date, or its number when it has no date, or its
-- password when it has neither. A row whose mark differs was made again, by whoever registered the name on the
-- website after the first one went, and is not the account's. A link made before this column has no mark, and
-- takes the mark of the row it finds first.
ALTER TABLE uxmauth_website_link ADD COLUMN row_mark VARCHAR(64);

-- The accounts an import made, one row each, under the source it read. A run that failed halfway runs again from
-- the start and writes over what it made itself; an account under a name this plugin held before the import is
-- never written over, because its mail, factors and sessions would stay with a password from another plugin.
CREATE TABLE IF NOT EXISTS uxmauth_import_account (
    source     VARCHAR(64) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    PRIMARY KEY (source, account_id)
);
