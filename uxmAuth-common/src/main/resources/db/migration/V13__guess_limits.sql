-- What was typed wrong for each account, from any address, kept where every member of a network reads it.
--
-- Wrong passwords, app codes, backup codes and mailed login codes count here whichever address or filter they
-- came through. failures counts them since the last lock; lock_step is the step of the next lock; locked_until
-- and last_failure are milliseconds since the epoch, 0 for never. A change bumps revision first, which locks the
-- row on every database before it is read.
CREATE TABLE IF NOT EXISTS uxmauth_account_lock (
    account_id   VARCHAR(36) NOT NULL,
    failures     INT         NOT NULL,
    lock_step    INT         NOT NULL,
    locked_until BIGINT      NOT NULL,
    last_failure BIGINT      NOT NULL,
    revision     BIGINT      NOT NULL,
    PRIMARY KEY (account_id)
);

-- How often codes of each kind went to each account, apart from the code itself, so a code that is spent, runs out
-- of tries or of time keeps its cooldown and the day's cap. last_sent is when the latest went, day_start when the
-- day its count runs in began, sent_today how many went since; milliseconds since the epoch, 0 for never. A
-- change bumps revision first, which locks the row on every database before it is read.
CREATE TABLE IF NOT EXISTS uxmauth_mail_send (
    account_id VARCHAR(36) NOT NULL,
    purpose    VARCHAR(16) NOT NULL,
    last_sent  BIGINT      NOT NULL,
    day_start  BIGINT      NOT NULL,
    sent_today INT         NOT NULL,
    revision   BIGINT      NOT NULL,
    PRIMARY KEY (account_id, purpose)
);

-- The host a remembered session's login came through, as the door reads it: the name without a port, in lower
-- case. Only a join from the same address through the same host resumes the session. A session kept before this
-- column holds no host and is resumed by no join, so its player types the password once more.
ALTER TABLE uxmauth_session ADD COLUMN host VARCHAR(255) DEFAULT '' NOT NULL;
