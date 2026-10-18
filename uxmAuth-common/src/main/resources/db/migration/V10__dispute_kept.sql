-- A dispute staff settled for the cracked holder stays, marked kept: the holder keeps the name, and the paid
-- owner who proves it again is told to change theirs instead of raising the dispute once more.
ALTER TABLE uxmauth_dispute ADD COLUMN kept BOOLEAN NOT NULL DEFAULT FALSE;
