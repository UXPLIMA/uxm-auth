-- The address each account has confirmed, kept where every member of a network reads it. An address waiting for
-- its code is not here: it is in uxmauth_mail_code until the code comes back. login_code says whether the
-- account takes its login code by mail.
CREATE TABLE IF NOT EXISTS uxmauth_email (
    account_id   VARCHAR(36)  NOT NULL,
    address      VARCHAR(254) NOT NULL,
    confirmed_at BIGINT       NOT NULL,
    login_code   BOOLEAN      NOT NULL,
    PRIMARY KEY (account_id)
);

-- The codes mailed to an account, one per purpose: confirming an address, the login code, a forgotten
-- password. code_hash is the code hashed as a password is. A row whose code_hash is empty holds no code; it is
-- the row a change locks before the first code exists, and revision is what the lock writes.
CREATE TABLE IF NOT EXISTS uxmauth_mail_code (
    account_id    VARCHAR(36)  NOT NULL,
    purpose       VARCHAR(16)  NOT NULL,
    address       VARCHAR(254) NOT NULL,
    code_hash     VARCHAR(255) NOT NULL,
    expires_at    BIGINT       NOT NULL,
    attempts_left INT          NOT NULL,
    sent_at       BIGINT       NOT NULL,
    revision      BIGINT       NOT NULL,
    PRIMARY KEY (account_id, purpose)
);
