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
