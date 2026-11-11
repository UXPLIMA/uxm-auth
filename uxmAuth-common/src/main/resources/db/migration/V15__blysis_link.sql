-- Which Blysis member each password account is, by the member's id, so a member who changes their username on the
-- website keeps their account here, and a member made later under an old username is somebody else. A member is
-- the account of one account at most.
CREATE TABLE IF NOT EXISTS uxmauth_blysis_link (
    account_id VARCHAR(36)  NOT NULL,
    member_id  VARCHAR(191) NOT NULL UNIQUE,
    PRIMARY KEY (account_id)
);
