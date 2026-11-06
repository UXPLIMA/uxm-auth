-- Finds an account by the Mojang id that proved it, which every premium connection asks. It is not unique: a
-- database from before imports refused a second account under one premium id may hold two, and a unique index
-- would then fail this migration and keep every server of the database from starting. The lookup answers the one
-- that played last, and an import no longer writes a premium id another account holds.
CREATE INDEX uxmauth_account_premium_id ON uxmauth_account (premium_id);
