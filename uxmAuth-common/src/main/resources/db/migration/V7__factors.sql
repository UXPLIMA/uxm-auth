-- The second factors of an account, kept where every member of a network reads them.
--
-- secret is the app secret sealed with AES-GCM under the key file or the network secret, never the secret
-- itself. last_step is the latest step a code was taken for, so a code is taken once across the network.
CREATE TABLE IF NOT EXISTS uxmauth_factor (
    account_id VARCHAR(36)  NOT NULL,
    secret     VARCHAR(255) NOT NULL,
    last_step  BIGINT       NOT NULL,
    created_at BIGINT       NOT NULL,
    PRIMARY KEY (account_id)
);

-- The backup codes of an account, hashed as passwords are. A used code is deleted, and a new set deletes
-- every old one.
CREATE TABLE IF NOT EXISTS uxmauth_backup_code (
    account_id VARCHAR(36)  NOT NULL,
    code_hash  VARCHAR(255) NOT NULL,
    PRIMARY KEY (account_id, code_hash)
);
