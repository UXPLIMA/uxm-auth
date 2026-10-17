package com.uxplima.auth.application.premium;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.name.PlayerName;

/**
 * The accounts that moved away from a name its paid owner proved: where a connection under the old name is sent,
 * and what the moved player is told at their next login. Every method blocks on the database.
 */
public final class Moves {

    private final AccountRepository accounts;

    public Moves(AccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    /** The name the account that held {@code old} holds now, if one moved away from it. */
    public Optional<PlayerName> movedTo(PlayerName old) {
        return accounts.renamedFrom(Objects.requireNonNull(old, "old"));
    }

    /** The move of this account its player has not read yet. */
    public Optional<AccountRepository.Renamed> untold(UUID id) {
        return accounts.untoldRename(Objects.requireNonNull(id, "id"));
    }

    /** The player of this account read of its move. */
    public void told(UUID id) {
        accounts.markRenameTold(Objects.requireNonNull(id, "id"));
    }
}
