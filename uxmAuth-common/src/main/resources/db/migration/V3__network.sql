-- What a network keeps beside the accounts.
--
-- One row per account: the server its player was on when they last left the network, so the proxy can send
-- them back there after login when the operator chose after-login = last-server.
CREATE TABLE IF NOT EXISTS uxmauth_last_server (
    account_id VARCHAR(36) NOT NULL,
    server     VARCHAR(64) NOT NULL,
    seen_at    BIGINT      NOT NULL,
    PRIMARY KEY (account_id)
);
