-- A dispute staff settled for the cracked holder stays, with the id of the account it was settled for: that
-- account keeps the name, and the paid owner who proves it again is told to change theirs. A later account under
-- the same name is not that holder, and the dispute stops it like any other.
ALTER TABLE uxmauth_dispute ADD COLUMN kept_for VARCHAR(36);
