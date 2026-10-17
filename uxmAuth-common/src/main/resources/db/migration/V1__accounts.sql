-- One row per account. The column types are the ones SQLite, MySQL, MariaDB and PostgreSQL read alike.
--
-- name                  the name as the player first wrote it, for display.
-- name_key              the name in lower case. Its unique index is what decides a race between two
--                       registrations of one name, so no read before a write is ever trusted for it.
-- kind                  CRACKED, PREMIUM or BEDROCK.
-- premium_id            the Mojang UUID once the name's owner has proved it, otherwise empty.
-- password              the stored hash, in the form its algorithm writes. Empty for an account that
--                       never set one, such as a premium account that logs in by its client.
-- registration_address  the address the account was made from. 45 characters hold any IPv6 text form.
-- *_at                  milliseconds since 1970 in UTC, because every database stores a BIGINT the same way.
CREATE TABLE IF NOT EXISTS uxmauth_account (
    id                   VARCHAR(36)  NOT NULL,
    name                 VARCHAR(16)  NOT NULL,
    name_key             VARCHAR(16)  NOT NULL,
    kind                 VARCHAR(16)  NOT NULL,
    premium_id           VARCHAR(36),
    password             VARCHAR(255),
    registered_at        BIGINT       NOT NULL,
    registration_address VARCHAR(45)  NOT NULL,
    last_login_at        BIGINT,
    last_address         VARCHAR(45),
    PRIMARY KEY (id),
    CONSTRAINT uxmauth_account_name_key UNIQUE (name_key)
);

CREATE INDEX uxmauth_account_registration_address ON uxmauth_account (registration_address);
