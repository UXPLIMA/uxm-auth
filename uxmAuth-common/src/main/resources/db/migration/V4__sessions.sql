-- Remembered sessions, kept where every member of a network reads them.
--
-- A row lets a player back from the same address before expires_at in without the password. A password
-- change, a logout and a choice of no sessions delete every row of the account.
CREATE TABLE IF NOT EXISTS uxmauth_session (
    account_id VARCHAR(36) NOT NULL,
    address    VARCHAR(45) NOT NULL,
    expires_at BIGINT      NOT NULL,
    PRIMARY KEY (account_id, address)
);

-- What each account chose, where the operator lets players choose: the box in the dialog, the toggle in the
-- form, or /session.
CREATE TABLE IF NOT EXISTS uxmauth_session_choice (
    account_id VARCHAR(36) NOT NULL,
    remember   BOOLEAN     NOT NULL,
    PRIMARY KEY (account_id)
);
