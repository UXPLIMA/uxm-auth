-- What each address did wrong at the door, kept where every member of a network reads it.
--
-- failures counts the wrong passwords since the last ban; ban_step is the step of the next ban;
-- banned_until and last_failure are milliseconds since the epoch, 0 for never. A change bumps
-- revision first, which locks the row on every database before it is read.
CREATE TABLE IF NOT EXISTS uxmauth_address (
    address      VARCHAR(45) NOT NULL,
    failures     INT         NOT NULL,
    ban_step     INT         NOT NULL,
    banned_until BIGINT      NOT NULL,
    last_failure BIGINT      NOT NULL,
    revision     BIGINT      NOT NULL,
    PRIMARY KEY (address)
);
